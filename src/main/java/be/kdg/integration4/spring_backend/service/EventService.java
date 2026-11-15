package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public List<Activity> getAllEvents() {
        return activityRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Activity> getEventById(Long id) {
        return activityRepository.findById(id);
    }

    @Transactional
    public Activity createEvent(Activity activity) {
        return activityRepository.save(activity);
    }
}