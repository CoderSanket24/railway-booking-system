$BASE      = 'http://localhost:8080/api/admin'
$AUTH_BASE = 'http://localhost:8080/api/auth'
$CT        = @{'Content-Type'='application/json'}
$script:pass = 0
$script:fail = 0
$script:TOKEN = ""

function Req {
    param($Name, $Method, $Url, $Body=$null)
    $h = @{'Content-Type'='application/json'}
    if ($script:TOKEN) { $h['Authorization'] = "Bearer $($script:TOKEN)" }
    try {
        if ($Body) {
            $r = Invoke-RestMethod -Method $Method -Uri $Url -Headers $h -Body ($Body|ConvertTo-Json -Depth 5) -ErrorAction Stop
        } else {
            $r = Invoke-RestMethod -Method $Method -Uri $Url -Headers $h -ErrorAction Stop
        }
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
        return $r
    } catch {
        Write-Host "  FAIL  $Name --> $($_.Exception.Message)" -ForegroundColor Red
        $script:fail++
        return $null
    }
}

# AUTH
Write-Host ""
Write-Host "=== AUTH ===" -ForegroundColor Magenta
$lr = Req "POST /auth/login admin" POST "$AUTH_BASE/login" @{username="admin";password="123456"}
if ($lr -and $lr.token) {
    $script:TOKEN = $lr.token
    Write-Host "  JWT token obtained (role: $($lr.role))" -ForegroundColor DarkGreen
} else {
    Write-Host "  No token - aborting" -ForegroundColor Red
    exit 1
}

# UNIT IV
Write-Host ""
Write-Host "=== UNIT IV: DEADLOCKS ===" -ForegroundColor Cyan
Req "GET bankers state"              GET  "$BASE/bankers"
Req "POST bankers safety"            POST "$BASE/bankers/safety"
Req "POST bankers request P1 1,0,2" POST "$BASE/bankers/request"  @{pid=1;request=@(1,0,2)}
Req "POST bankers request exceed"    POST "$BASE/bankers/request"  @{pid=2;request=@(9,9,9)}
Req "GET deadlock RAG"               GET  "$BASE/deadlock"
Req "POST deadlock simulate DL"      POST "$BASE/deadlock/simulate"  @{scenario="DEADLOCK"}
Req "POST deadlock detect"           POST "$BASE/deadlock/detect"
Req "POST deadlock simulate SAFE"    POST "$BASE/deadlock/simulate"  @{scenario="SAFE"}
Req "POST deadlock detect safe"      POST "$BASE/deadlock/detect"
Req "GET deadlock recovery"          GET  "$BASE/deadlock/recovery"
Req "POST deadlock recovery reset"   POST "$BASE/deadlock/recovery/reset"
Req "POST deadlock recover TERM"     POST "$BASE/deadlock/recover"  @{strategy="TERMINATION"}
Req "POST deadlock recovery reset 2" POST "$BASE/deadlock/recovery/reset"
Req "POST deadlock recover PREEMPT"  POST "$BASE/deadlock/recover"  @{strategy="PREEMPTION"}

# UNIT V - Memory
Write-Host ""
Write-Host "=== UNIT V: MEMORY ===" -ForegroundColor Cyan
Req "GET memory state"                   GET  "$BASE/memory"
Req "POST memory alloc-fixed 64KB"       POST "$BASE/memory/allocate-fixed"   @{pid=10;sizeKB=64;processName="User10"}
Req "POST memory alloc-fixed 100KB"      POST "$BASE/memory/allocate-fixed"   @{pid=11;sizeKB=100;processName="User11"}
Req "POST memory alloc-fixed oversized"  POST "$BASE/memory/allocate-fixed"   @{pid=12;sizeKB=250;processName="TooBig"}
Req "POST memory alloc-dyn FIRST_FIT"    POST "$BASE/memory/allocate-dynamic" @{pid=20;sizeKB=200;processName="U20";strategy="FIRST_FIT"}
Req "POST memory alloc-dyn BEST_FIT"     POST "$BASE/memory/allocate-dynamic" @{pid=21;sizeKB=150;processName="U21";strategy="BEST_FIT"}
Req "POST memory alloc-dyn WORST_FIT"    POST "$BASE/memory/allocate-dynamic" @{pid=22;sizeKB=80; processName="U22";strategy="WORST_FIT"}
Req "POST memory alloc-dyn NEXT_FIT"     POST "$BASE/memory/allocate-dynamic" @{pid=23;sizeKB=90; processName="U23";strategy="NEXT_FIT"}
Req "POST memory reset"                  POST "$BASE/memory/reset"

# Buddy System
Write-Host ""
Write-Host "--- Buddy System ---" -ForegroundColor Yellow
Req "GET buddy state"                GET  "$BASE/buddy"
Req "POST buddy alloc 70KB"          POST "$BASE/buddy/allocate"   @{pid=5;sizeKB=70; processName="U5"}
Req "POST buddy alloc 200KB"         POST "$BASE/buddy/allocate"   @{pid=6;sizeKB=200;processName="U6"}
Req "POST buddy alloc 50KB"          POST "$BASE/buddy/allocate"   @{pid=7;sizeKB=50; processName="U7"}
Req "POST buddy dealloc addr=0"      POST "$BASE/buddy/deallocate" @{address=0}
Req "POST buddy dealloc addr=256"    POST "$BASE/buddy/deallocate" @{address=256}
Req "GET buddy after dealloc"        GET  "$BASE/buddy"

# Page Replacement
Write-Host ""
Write-Host "--- Page Replacement ---" -ForegroundColor Yellow
Req "POST page-replacement 4fr" POST "$BASE/page-replacement" @{frames=4;references=@(1,2,3,4,1,2,5,1,2,3,4,5)}
Req "POST page-replacement 3fr" POST "$BASE/page-replacement" @{frames=3;references=@(7,0,1,2,0,3,0,4,2,3,0,3,2)}
Req "POST page-replacement def" POST "$BASE/page-replacement" @{frames=4}

# TLB
Write-Host ""
Write-Host "--- TLB ---" -ForegroundColor Yellow
Req "GET tlb state"              GET  "$BASE/tlb"
Req "POST tlb demo"              POST "$BASE/tlb/demo"
Req "POST tlb translate 0x0A20"  POST "$BASE/tlb/translate"  @{logicalAddress=0x0A20}
Req "POST tlb translate again"   POST "$BASE/tlb/translate"  @{logicalAddress=0x0A20}
Req "POST tlb translate 0x0B20"  POST "$BASE/tlb/translate"  @{logicalAddress=0x0B20}
Req "POST tlb flush"             POST "$BASE/tlb/flush"
Req "POST tlb translate post flush" POST "$BASE/tlb/translate" @{logicalAddress=0x0A20}
Req "GET tlb final stats"        GET  "$BASE/tlb"

# UNIT VI
Write-Host ""
Write-Host "=== UNIT VI: I/O AND FILE MGMT ===" -ForegroundColor Cyan

# Disk Scheduling
Req "POST disk-scheduling head=50"  POST "$BASE/disk-scheduling" @{head=50; requests=@(82,170,43,140,24,16,190)}
Req "POST disk-scheduling head=100" POST "$BASE/disk-scheduling" @{head=100;requests=@(55,58,39,18,90,160,150,38,184)}
Req "POST disk-scheduling defaults" POST "$BASE/disk-scheduling" @{head=50}

# IO Buffer
Write-Host ""
Write-Host "--- IO Buffer ---" -ForegroundColor Yellow
Req "GET io-buffer state"          GET  "$BASE/io-buffer"
Req "POST io-buffer demo SINGLE"   POST "$BASE/io-buffer/demo"  @{type="SINGLE"}
Req "POST io-buffer demo DOUBLE"   POST "$BASE/io-buffer/demo"  @{type="DOUBLE"}
Req "POST io-buffer demo CIRCULAR" POST "$BASE/io-buffer/demo"  @{type="CIRCULAR"}

# File System
Write-Host ""
Write-Host "--- File System ---" -ForegroundColor Yellow
Req "GET file-system preloaded"        GET  "$BASE/file-system"
Req "POST file-system create INDEXED"  POST "$BASE/file-system/create" @{name="TEST_IDX.bkr"; organization="INDEXED";   owner="admin"}
Req "POST file-system create SEQ"      POST "$BASE/file-system/create" @{name="TEST_SEQ.bkr"; organization="SEQUENTIAL";owner="admin"}
Req "POST file-system create LINKED"   POST "$BASE/file-system/create" @{name="TEST_LNK.bkr"; organization="LINKED";    owner="admin"}
Req "POST file-system write INDEXED"   POST "$BASE/file-system/write"  @{fileName="TEST_IDX.bkr";record="BK001:User42:Seat-A3"}
Req "POST file-system write SEQ"       POST "$BASE/file-system/write"  @{fileName="TEST_SEQ.bkr";record="BK002:User17:Seat-B1"}
Req "POST file-system write LINKED"    POST "$BASE/file-system/write"  @{fileName="TEST_LNK.bkr";record="BK003:User88:Seat-C5"}
Req "POST file-system read INDEXED 0"  POST "$BASE/file-system/read"   @{fileName="TEST_IDX.bkr"; recordIndex=0}
Req "POST file-system read SEQ 5"      POST "$BASE/file-system/read"   @{fileName="TEST_SEQ.bkr"; recordIndex=5}
Req "POST file-system read LINKED 3"   POST "$BASE/file-system/read"   @{fileName="TEST_LNK.bkr"; recordIndex=3}
Req "POST file-system read preloaded"  POST "$BASE/file-system/read"   @{fileName="12951_2024-06-15.bkr";recordIndex=2}
Req "POST file-system share READ U42"  POST "$BASE/file-system/share"  @{fileName="12951_2024-06-15.bkr";user="User42";mode="READ"}
Req "POST file-system share READ U55"  POST "$BASE/file-system/share"  @{fileName="12951_2024-06-15.bkr";user="User55";mode="READ"}
Req "POST file-system share WRITE U88" POST "$BASE/file-system/share"  @{fileName="15001_2024-06-15.bkr";user="User88";mode="WRITE"}
Req "POST file-system share WRITE blk" POST "$BASE/file-system/share"  @{fileName="15001_2024-06-15.bkr";user="User99";mode="WRITE"}

# Regression
Write-Host ""
Write-Host "=== REGRESSION ===" -ForegroundColor Cyan
Req "GET monitor existing"   GET "$BASE/monitor"
Req "GET scheduler existing" GET "$BASE/scheduler"

# Summary
$total = $script:pass + $script:fail
Write-Host ""
Write-Host "============================================" -ForegroundColor White
Write-Host "  TOTAL  : $total"          -ForegroundColor White
Write-Host "  PASSED : $($script:pass)" -ForegroundColor Green
Write-Host "  FAILED : $($script:fail)" -ForegroundColor Red
Write-Host "============================================" -ForegroundColor White
if ($script:fail -eq 0) {
    Write-Host "  ALL TESTS PASSED!" -ForegroundColor Green
}
