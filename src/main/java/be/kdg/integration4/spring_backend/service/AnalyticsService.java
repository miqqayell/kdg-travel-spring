package be.kdg.integration4.spring_backend.service;




import be.kdg.integration4.spring_backend.controller.web.dto.*;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.TravelGroupMembership;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import be.kdg.integration4.spring_backend.repository.TravelGroupMembershipRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserService userService;
    private final TravelGroupRepository travelGroupRepository;
    private final TravelGroupMembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public UserAnalyticsDto getStatisticsForUser(String originalUserId) {
        User user = userService.getCurrentUser(originalUserId);
        LocalDateTime now = LocalDateTime.now();

        long createdTrips = travelGroupRepository
                .countByCreatedByUserIdAndStatus(user.getId(), TravelGroupStatus.ACTIVE);

        long joinedTrips = membershipRepository
                .countByUserIdAndTravelGroupStatus(user.getId(), TravelGroupStatus.ACTIVE);

        List<TravelGroupMembership> pastMemberships = membershipRepository
                .findByUserIdAndTravelGroupStatusAndTravelGroupMeetupTimeBefore(user.getId(), TravelGroupStatus.ACTIVE, now);

        double co2SavingsKg = calculateCo2Savings(pastMemberships);

        return new UserAnalyticsDto(createdTrips, joinedTrips, co2SavingsKg, getUserCo2SavingsOverTime(pastMemberships));
    }


    @Transactional(readOnly = true)
    public int getTotalTripsForUser(Long userId) {
        // We count past memberships (trips that actually happened)
        LocalDateTime now = LocalDateTime.now();
        List<TravelGroupMembership> pastMemberships = membershipRepository
                .findByUserIdAndTravelGroupStatusAndTravelGroupMeetupTimeBefore(userId, TravelGroupStatus.ACTIVE, now);
        return pastMemberships.size();
    }

    @Transactional(readOnly = true)
    public double getTotalCo2SavedForUser(Long userId) {
        // Reuses your excellent existing CO2 math
        LocalDateTime now = LocalDateTime.now();
        List<TravelGroupMembership> pastMemberships = membershipRepository
                .findByUserIdAndTravelGroupStatusAndTravelGroupMeetupTimeBefore(userId, TravelGroupStatus.ACTIVE, now);
        return calculateCo2Savings(pastMemberships);
    }

    @Transactional(readOnly = true)
    public int getTotalTransitTripsForUser(Long userId) {
        // Counts only the trips taken via PUBLIC_TRANSPORT
        LocalDateTime now = LocalDateTime.now();
        List<TravelGroupMembership> pastMemberships = membershipRepository
                .findByUserIdAndTravelGroupStatusAndTravelGroupMeetupTimeBefore(userId, TravelGroupStatus.ACTIVE, now);

        return (int) pastMemberships.stream()
                .filter(m -> m.getTravelGroup() != null && m.getTravelGroup().getTransportationMode() == TransportMode.PUBLIC_TRANSPORT)
                .count();
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsDto getAdminAnalytics() {
        List<TravelGroup> pastGroups = travelGroupRepository
                .findByStatusAndMeetupTimeBeforeOrderByMeetupTime(TravelGroupStatus.ACTIVE, LocalDateTime.now());

        return new AdminAnalyticsDto(
                getAverageGroupSize(),
                getMostPopularActivities(),
                getPopularTransportModes(),
                getPeakTravelTimes(pastGroups),
                getCo2SavingsOverTime(pastGroups)
        );
    }

    private double calculateCo2Savings(List<TravelGroupMembership> memberships) {
        return memberships.stream()
                .mapToDouble(membership -> calculateGroupCo2Saving(membership.getTravelGroup()))
                .sum();
    }

    private List<Co2SavingOverTimeDto> getUserCo2SavingsOverTime(List<TravelGroupMembership> memberships) {
        List<Co2SavingOverTimeDto> result = new ArrayList<>();
        double runningTotal = 0.0;

        for (TravelGroupMembership membership : memberships) {
            TravelGroup group = membership.getTravelGroup();

            if (group == null || group.getMeetupTime() == null) {
                continue;
            }

            runningTotal += calculateGroupCo2Saving(group);

            result.add(new Co2SavingOverTimeDto(
                    group.getMeetupTime().toLocalDate().toString(),
                    runningTotal
            ));
        }

        return result;
    }

    private double getAverageGroupSize() {
        Double average = travelGroupRepository.findAverageGroupSizeByStatus(TravelGroupStatus.ACTIVE);
        return average == null ? 0.0 : average;
    }



    private List<ActivityPopularityDto> getMostPopularActivities() {
        return travelGroupRepository.findMostPopularActivities(TravelGroupStatus.ACTIVE)
                .stream()
                .map(row -> new ActivityPopularityDto(
                        (String) row[0],
                        (Long) row[1]
                ))
                .toList();
    }

    private List<TransportModePopularityDto> getPopularTransportModes() {
        return travelGroupRepository.findPopularTransportModes(TravelGroupStatus.ACTIVE)
                .stream()
                .map(row -> new TransportModePopularityDto(
                        ((TransportMode) row[0]).name(),
                        (Long) row[1]
                ))
                .toList();
    }

    private List<PeakTravelTimeDto> getPeakTravelTimes(List<TravelGroup> groups) {
        Map<Integer, Long> tripsByHour = groups.stream()
                .filter(group -> group.getMeetupTime() != null)
                .collect(Collectors.groupingBy(
                        group -> group.getMeetupTime().getHour(),
                        Collectors.counting()
                ));

        List<PeakTravelTimeDto> result = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            result.add(new PeakTravelTimeDto(
                    String.format("%02d:00", hour),
                    tripsByHour.getOrDefault(hour, 0L)
            ));
        }

        return result;
    }

    private List<Co2SavingOverTimeDto> getCo2SavingsOverTime(List<TravelGroup> groups) {
        List<Co2SavingOverTimeDto> result = new ArrayList<>();
        double runningTotal = 0.0;

        for (TravelGroup group : groups) {
            if (group.getMeetupTime() == null) {
                continue;
            }

            runningTotal += calculateGroupCo2Saving(group);

            result.add(new Co2SavingOverTimeDto(
                    group.getMeetupTime().toLocalDate().toString(),
                    runningTotal
            ));
        }

        return result;
    }

    private double calculateGroupCo2Saving(TravelGroup group) {
        if (group == null || group.getDistanceKm() == null || group.getTransportationMode() == null) {
            return 0.0;
        }

        long passengerCount = Math.max(group.getMemberCount(), 1);

        return calculateTripSaving(
                group.getDistanceKm(),
                group.getTransportationMode(),
                passengerCount
        );
    }

    private double calculateTripSaving(double distanceKm, TransportMode mode, long passengerCount) {

        // 120g CO2/km = 0.120 kg CO2/km
        double carEmissionKgPerKm = 0.120;


        double selectedEmissionKgPerKm = switch (mode) {
            case WALK, BIKE -> 0.0;

            case PUBLIC_TRANSPORT -> 0.04;

            case CARPOOL -> carEmissionKgPerKm / passengerCount;
        };

        return distanceKm * (carEmissionKgPerKm - selectedEmissionKgPerKm);
    }


}
