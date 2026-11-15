package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.controller.web.dto.ReportDto;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.ReportType;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final TravelGroupRepository travelGroupRepository;

    @GetMapping("/user/{userId}")
    public String showUserReportForm(@PathVariable Long userId, Model model) {
        User reportedUser = userRepository.findById(userId).orElseThrow();
        ReportDto request = new ReportDto();
        request.setReportedUserId(userId);
        model.addAttribute("reportRequest", request);
        model.addAttribute("reportTypes", ReportType.values());
        model.addAttribute("targetTitle", reportedUser.getUsername());
        model.addAttribute("targetType", "user");
        model.addAttribute("formAction", "/reports/user/" + userId);
        return "report-form";
    }

    @PostMapping("/user/{userId}")
    public String submitUserReport(@PathVariable Long userId,
                                   @Valid @ModelAttribute("reportRequest") ReportDto request,
                                   BindingResult bindingResult,
                                   Model model,
                                   @AuthenticationPrincipal OidcUser oidcUser,
                                   RedirectAttributes redirectAttributes) {
        request.setReportedUserId(userId);

        if (bindingResult.hasErrors()) {
            User reportedUser = userRepository.findById(userId).orElseThrow();
            model.addAttribute("reportTypes", ReportType.values());
            model.addAttribute("targetTitle", reportedUser.getUsername());
            model.addAttribute("targetType", "user");
            model.addAttribute("formAction", "/reports/user/" + userId);
            return "report-form";
        }

        try {
            reportService.createReport(request, oidcUser.getSubject());
            // Message about sent report
            redirectAttributes.addFlashAttribute("successMessage", "Your report has been securely submitted. Our moderators will review it soon.");
            return "redirect:/users/" + userId;
        } catch (BadRequestException e) {
            User reportedUser = userRepository.findById(userId).orElseThrow();
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("reportTypes", ReportType.values());
            model.addAttribute("targetTitle", reportedUser.getUsername());
            model.addAttribute("targetType", "user");
            model.addAttribute("formAction", "/reports/user/" + userId);
            return "report-form";
        }
    }

    @GetMapping("/activity/{activityId}")
    public String showActivityReportForm(@PathVariable Long activityId, Model model) {
        Activity activity = activityRepository.findById(activityId).orElseThrow();
        ReportDto request = new ReportDto();
        request.setReportedActivityId(activityId);
        model.addAttribute("reportRequest", request);
        model.addAttribute("reportTypes", ReportType.values());
        model.addAttribute("targetTitle", activity.getTitle());
        model.addAttribute("targetType", "event");
        model.addAttribute("formAction", "/reports/activity/" + activityId);
        return "report-form";
    }

    @PostMapping("/activity/{activityId}")
    public String submitActivityReport(@PathVariable Long activityId,
                                       @Valid @ModelAttribute("reportRequest") ReportDto request,
                                       BindingResult bindingResult,
                                       Model model,
                                       @AuthenticationPrincipal OidcUser oidcUser,
                                       RedirectAttributes redirectAttributes) {
        request.setReportedActivityId(activityId);

        if (bindingResult.hasErrors()) {
            Activity activity = activityRepository.findById(activityId).orElseThrow();
            model.addAttribute("reportTypes", ReportType.values());
            model.addAttribute("targetTitle", activity.getTitle());
            model.addAttribute("targetType", "event");
            model.addAttribute("formAction", "/reports/activity/" + activityId);
            return "report-form";
        }

        try {
            reportService.createReport(request, oidcUser.getSubject());
            // Message about sent report
            redirectAttributes.addFlashAttribute("successMessage", "Your report has been securely submitted. Our moderators will review it soon.");
            return "redirect:/activities/" + activityId;
        } catch (BadRequestException e) {
            Activity activity = activityRepository.findById(activityId).orElseThrow();
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("reportTypes", ReportType.values());
            model.addAttribute("targetTitle", activity.getTitle());
            model.addAttribute("targetType", "event");
            model.addAttribute("formAction", "/reports/activity/" + activityId);
            return "report-form";
        }
    }

    @GetMapping("/travel-group/{groupId}")
    public String showTravelGroupReportForm(@PathVariable Long groupId, Model model) {
        TravelGroup group = travelGroupRepository.findById(groupId).orElseThrow();
        ReportDto request = new ReportDto();
        request.setReportedTravelGroupId(groupId);
        model.addAttribute("reportRequest", request);
        model.addAttribute("reportTypes", ReportType.values());
        model.addAttribute("targetTitle", group.getTitle());
        model.addAttribute("targetType", "travel group");
        model.addAttribute("formAction", "/reports/travel-group/" + groupId);
        return "report-form";
    }

    @PostMapping("/travel-group/{groupId}")
    public String submitTravelGroupReport(@PathVariable Long groupId,
                                          @Valid @ModelAttribute("reportRequest") ReportDto request,
                                          BindingResult bindingResult,
                                          Model model,
                                          @AuthenticationPrincipal OidcUser oidcUser,
                                          RedirectAttributes redirectAttributes) {
        request.setReportedTravelGroupId(groupId);

        if (bindingResult.hasErrors()) {
            TravelGroup group = travelGroupRepository.findById(groupId).orElseThrow();
            model.addAttribute("reportTypes", ReportType.values());
            model.addAttribute("targetTitle", group.getTitle());
            model.addAttribute("targetType", "travel group");
            model.addAttribute("formAction", "/reports/travel-group/" + groupId);
            return "report-form";
        }

        try {
            reportService.createReport(request, oidcUser.getSubject());
            // Message about sent report
            redirectAttributes.addFlashAttribute("successMessage", "Your report has been securely submitted. Our moderators will review it soon.");
            return "redirect:/travel-groups/" + groupId;
        } catch (BadRequestException e) {
            TravelGroup group = travelGroupRepository.findById(groupId).orElseThrow();
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("reportTypes", ReportType.values());
            model.addAttribute("targetTitle", group.getTitle());
            model.addAttribute("targetType", "travel group");
            model.addAttribute("formAction", "/reports/travel-group/" + groupId);
            return "report-form";
        }
    }
}