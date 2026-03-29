package com.vit.railway_os.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pnr;

    @Column(nullable = false)
    private int userId;

    @Column(nullable = false)
    private String trainNumber;

    @Column(nullable = false)
    private String trainName;

    @Column(nullable = false)
    private String fromStation;

    @Column(nullable = false)
    private String toStation;

    @Column(nullable = false)
    private String departure;

    @Column(nullable = false)
    private int seats;

    @Column(nullable = false)
    private int totalPrice;

    @Column(nullable = false)
    private String bookingDate;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String passengerName;

    // Default constructor required by JPA
    public Booking() {}

    public Booking(String pnr, int userId, String trainNumber, String trainName,
                   String fromStation, String toStation, String departure,
                   int seats, int totalPrice, String passengerName) {
        this.pnr = pnr;
        this.userId = userId;
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.departure = departure;
        this.seats = seats;
        this.totalPrice = totalPrice;
        this.passengerName = passengerName;
        this.status = "Confirmed";
        this.bookingDate = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getPnr() { return pnr; }
    public void setPnr(String pnr) { this.pnr = pnr; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public String getTrainName() { return trainName; }
    public void setTrainName(String trainName) { this.trainName = trainName; }
    public String getFromStation() { return fromStation; }
    public void setFromStation(String fromStation) { this.fromStation = fromStation; }
    public String getToStation() { return toStation; }
    public void setToStation(String toStation) { this.toStation = toStation; }
    public String getDeparture() { return departure; }
    public void setDeparture(String departure) { this.departure = departure; }
    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }
    public int getTotalPrice() { return totalPrice; }
    public void setTotalPrice(int totalPrice) { this.totalPrice = totalPrice; }
    public String getBookingDate() { return bookingDate; }
    public void setBookingDate(String bookingDate) { this.bookingDate = bookingDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
}
