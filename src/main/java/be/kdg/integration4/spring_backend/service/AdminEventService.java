package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public List<Activity> getPendingEvents() {
        return activityRepository.findByVerificationStatusOrderByStartTimeAscIdAsc(VerificationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<Activity> getAllEvents() {
        return activityRepository.findAllByOrderByStartTimeAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Activity getEventById(Long id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
    }

    @Transactional(readOnly = true)
    public long countPendingEvents() {
        return activityRepository.countByVerificationStatus(VerificationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long countVerifiedEvents() {
        return activityRepository.countByVerificationStatus(VerificationStatus.VERIFIED);
    }

    @Transactional(readOnly = true)
    public long countTotalEvents() {
        return activityRepository.count();
    }

    @Transactional
    public void verifyEvent(Long id) {
        Activity activity = getEventById(id);
        activity.setVerificationStatus(VerificationStatus.VERIFIED);
    }

    @Transactional
    public void rejectEvent(Long id) {
        Activity activity = getEventById(id);
        activity.setVerificationStatus(VerificationStatus.REJECTED);
    }

    @Transactional
    public void deleteEvent(Long id) {
        activityRepository.deleteById(id);
    }
}
