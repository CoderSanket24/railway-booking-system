package com.vit.railway_os.repository;

import com.vit.railway_os.model.Train;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainRepository extends JpaRepository<Train, Long> {
    // Spring automatically writes the SQL for this based on the method name!
    Optional<Train> findByTrainNumber(String trainNumber);
}