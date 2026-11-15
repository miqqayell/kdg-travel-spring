package be.kdg.integration4.spring_backend.mappers;

import be.kdg.integration4.spring_backend.controller.web.dto.ActivityDTO;
import be.kdg.integration4.spring_backend.domain.Activity;
import org.springframework.stereotype.Component;

@Component
public class ActivityMapper {

    public ActivityDTO toDto(Activity activity) {
        if (activity == null) return null;

        return new ActivityDTO(
                activity.getId(),
                activity.getTitle(),
                activity.getType(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getDescription(),
                activity.getLocationLatitude(),
                activity.getLocationLongitude(),
                activity.getLocationAddress(),
                activity.getVerificationStatus(),
                activity.getImageUrl(),
                activity.getCreatedByUser() != null
                        ? activity.getCreatedByUser().getUsername()
                        : null
        );
    }

    public Activity toEntity(ActivityDTO dto) {
        if (dto == null) return null;

        Activity activity = new Activity();

        activity.setId(dto.getId());
        activity.setTitle(dto.getTitle());
        activity.setType(dto.getType());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setDescription(dto.getDescription());
        activity.setLocationLatitude(dto.getLocationLatitude());
        activity.setLocationLongitude(dto.getLocationLongitude());
        activity.setLocationAddress(dto.getLocationAddress());
        activity.setVerificationStatus(dto.getVerificationStatus());
        activity.setImageUrl(dto.getImageUrl());

        return activity;
    }
}
