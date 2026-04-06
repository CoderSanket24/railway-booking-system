$H = @{'Content-Type'='application/json'}

# Login
$tok = (Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/auth/login' -Headers $H -Body '{"username":"admin","password":"123456"}').token
Write-Host "TOKEN: $($tok.Substring(0,40))..." -ForegroundColor DarkGray
$A = @{'Content-Type'='application/json'; 'Authorization'="Bearer $tok"}

# 1. Banker's Safety
Write-Host ""
Write-Host "== 1. BANKERS SAFETY ==" -ForegroundColor Cyan
$r1 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/bankers/safety' -Headers $A
Write-Host "  safe         : $($r1.safe)"
Write-Host "  safeSequence : $($r1.safeSequence -join ' -> ')"
Write-Host "  steps:"
$r1.steps | ForEach-Object { Write-Host "    $_" }

# 2. Deadlock Detection
Write-Host ""
Write-Host "== 2. DEADLOCK DETECT (simulating circular wait) ==" -ForegroundColor Cyan
Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/deadlock/simulate' -Headers $A -Body '{"scenario":"DEADLOCK"}' | Out-Null
$r2 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/deadlock/detect' -Headers $A
Write-Host "  deadlocked : $($r2.deadlocked)"
Write-Host "  cycle      : $($r2.cycle -join ' -> ')"

# 2b. Safe scenario
Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/deadlock/simulate' -Headers $A -Body '{"scenario":"SAFE"}' | Out-Null
$r2b = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/deadlock/detect' -Headers $A
Write-Host "  (SAFE scenario) deadlocked : $($r2b.deadlocked)"

# 3. Deadlock Recovery
Write-Host ""
Write-Host "== 3. DEADLOCK RECOVERY (TERMINATION) ==" -ForegroundColor Cyan
Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/deadlock/recovery/reset' -Headers $A | Out-Null
$r3 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/deadlock/recover' -Headers $A -Body '{"strategy":"TERMINATION"}'
Write-Host "  resolved  : $($r3.resolved)"
Write-Host "  strategy  : $($r3.strategy)"
Write-Host "  affected  : $($r3.affected -join ', ')"
Write-Host "  steps:"
$r3.steps | ForEach-Object { Write-Host "    $_" }

# 4. Memory - Buddy System
Write-Host ""
Write-Host "== 4. BUDDY SYSTEM (allocate 70KB -> expect 128KB block) ==" -ForegroundColor Cyan
$r4 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/buddy/allocate' -Headers $A -Body '{"pid":99,"sizeKB":70,"processName":"TestUser"}'
Write-Host "  address : $($r4.address)KB"
Write-Host "  success : $($r4.success)"
$r4.state.recentLog | Select-Object -Last 4 | ForEach-Object { Write-Host "  $_" }

# 5. Page Replacement Comparison
Write-Host ""
Write-Host "== 5. PAGE REPLACEMENT (4 frames, ref=[1,2,3,4,1,2,5,1,2,3,4,5]) ==" -ForegroundColor Cyan
$r5 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/page-replacement' -Headers $A -Body '{"frames":4,"references":[1,2,3,4,1,2,5,1,2,3,4,5]}'
$r5.comparison | ForEach-Object {
    Write-Host ("  " + $_.algorithm.PadRight(10) + " faults=" + $_.pageFaults + "  rate=" + $_.faultRate)
}

# 6. TLB
Write-Host ""
Write-Host "== 6. TLB DEMO ==" -ForegroundColor Cyan
$r6 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/tlb/demo' -Headers $A
Write-Host "  hits     : $($r6.hits)"
Write-Host "  misses   : $($r6.misses)"
Write-Host "  hitRate  : $($r6.hitRate)"
Write-Host "  missRate : $($r6.missRate)"
Write-Host "  EMAT     : $($r6.ematNs)"

# 7. Disk Scheduling Comparison
Write-Host ""
Write-Host "== 7. DISK SCHEDULING (head=50, requests=[82,170,43,140,24,16,190]) ==" -ForegroundColor Cyan
$r7 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/disk-scheduling' -Headers $A -Body '{"head":50,"requests":[82,170,43,140,24,16,190]}'
$r7.comparison | ForEach-Object {
    Write-Host ("  " + $_.algorithm.PadRight(10) + " totalMovement=" + $_.totalMovement + " cylinders")
}

# 8. IO Buffer
Write-Host ""
Write-Host "== 8. IO BUFFER DEMO (DOUBLE) ==" -ForegroundColor Cyan
$r8 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/io-buffer/demo' -Headers $A -Body '{"type":"DOUBLE"}'
Write-Host "  totalProduced : $($r8.totalProduced)"
Write-Host "  totalConsumed : $($r8.totalConsumed)"
Write-Host "  fullStalls    : $($r8.bufferFullStalls)"
Write-Host "  log (last 5):"
$r8.recentLog | Select-Object -Last 5 | ForEach-Object { Write-Host "    $_" }

# 9. File System - read comparison across org types
Write-Host ""
Write-Host "== 9. FILE SYSTEM READ (record index 4, all 3 organizations) ==" -ForegroundColor Cyan
@(
    @{file="12951_2024-06-15.bkr"; org="INDEXED"},
    @{file="15001_2024-06-15.bkr"; org="SEQUENTIAL"},
    @{file="16032_2024-06-16.bkr"; org="LINKED"}
) | ForEach-Object {
    $body = '{"fileName":"' + $_.file + '","recordIndex":4}'
    $rf = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/file-system/read' -Headers $A -Body $body
    Write-Host ("  " + $_.org.PadRight(12) + " diskOps=" + $rf.diskOps + "  -> " + $rf.steps[0])
}

# 10. File Sharing
Write-Host ""
Write-Host "== 10. FILE SHARING (concurrent readers, exclusive writer) ==" -ForegroundColor Cyan
$s1 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/file-system/share' -Headers $A -Body '{"fileName":"12951_2024-06-15.bkr","user":"User42","mode":"READ"}'
$s2 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/file-system/share' -Headers $A -Body '{"fileName":"12951_2024-06-15.bkr","user":"User55","mode":"READ"}'
$s3 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/file-system/share' -Headers $A -Body '{"fileName":"15001_2024-06-15.bkr","user":"User88","mode":"WRITE"}'
$s4 = Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/admin/file-system/share' -Headers $A -Body '{"fileName":"15001_2024-06-15.bkr","user":"User99","mode":"WRITE"}'
Write-Host "  User42 READ  granted: $($s1.granted)  (expect: True)"
Write-Host "  User55 READ  granted: $($s2.granted)  (expect: True  - concurrent reader OK)"
Write-Host "  User88 WRITE granted: $($s3.granted)  (expect: True)"
Write-Host "  User99 WRITE granted: $($s4.granted)  (expect: False - exclusive lock held)"

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  ALL 10 SPOT-CHECKS COMPLETED" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
