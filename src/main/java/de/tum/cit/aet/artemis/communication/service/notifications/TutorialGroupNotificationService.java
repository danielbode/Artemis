package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_UPDATED;
import static de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotificationFactory.createTutorialGroupNotification;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ApiNotPresentException;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupNotificationApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupRegistrationApi;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;

@Profile(PROFILE_CORE)
@Service
public class TutorialGroupNotificationService {

    private final Optional<TutorialGroupNotificationApi> tutorialGroupNotificationApi;

    private final Optional<TutorialGroupRegistrationApi> tutorialGroupRegistrationApi;

    private final WebsocketMessagingService websocketMessagingService;

    private final NotificationSettingsService notificationSettingsService;

    private final GeneralInstantNotificationService notificationService;

    public TutorialGroupNotificationService(Optional<TutorialGroupNotificationApi> tutorialGroupNotificationApi,
            Optional<TutorialGroupRegistrationApi> tutorialGroupRegistrationApi, WebsocketMessagingService websocketMessagingService,
            NotificationSettingsService notificationSettingsService, GeneralInstantNotificationService notificationService) {
        this.tutorialGroupNotificationApi = tutorialGroupNotificationApi;
        this.tutorialGroupRegistrationApi = tutorialGroupRegistrationApi;
        this.websocketMessagingService = websocketMessagingService;
        this.notificationSettingsService = notificationSettingsService;
        this.notificationService = notificationService;
    }

    /**
     * Notify registered students about a deleted tutorial group
     *
     * @param tutorialGroup the deleted tutorial group
     */
    public void notifyAboutTutorialGroupDeletion(TutorialGroup tutorialGroup) {
        saveAndSend(createTutorialGroupNotification(TutorialGroup.preventCircularJsonConversion(tutorialGroup), TUTORIAL_GROUP_DELETED), true);
    }

    /**
     * Notify registered students about an updated tutorial group
     *
     * @param tutorialGroup    the updated tutorial group
     * @param notifyTutor      whether the tutor should be notified about the update via email
     * @param notificationText the notification text
     */
    public void notifyAboutTutorialGroupUpdate(TutorialGroup tutorialGroup, boolean notifyTutor, String notificationText) {
        saveAndSend(createTutorialGroupNotification(TutorialGroup.preventCircularJsonConversion(tutorialGroup), TUTORIAL_GROUP_UPDATED, notificationText), notifyTutor);
    }

    private void saveAndSend(TutorialGroupNotification notification, boolean notifyTutor) {
        TutorialGroupNotificationApi api = tutorialGroupNotificationApi.orElseThrow(() -> new ApiNotPresentException(TutorialGroupNotificationApi.class, PROFILE_CORE));
        api.save(notification);
        sendNotificationViaWebSocket(notification);
        sendInstantNotification(notification, notifyTutor);
    }

    private void sendInstantNotification(TutorialGroupNotification notification, boolean notifyTutor) {
        if (notificationSettingsService.checkNotificationTypeForInstantNotificationSupport(notification.notificationType)) {
            var usersToMail = findUsersToNotify(notification, notifyTutor);
            if (!usersToMail.isEmpty()) {
                notificationService.sendNotification(notification, usersToMail, notification.getTutorialGroup());
            }
        }
    }

    private void sendNotificationViaWebSocket(TutorialGroupNotification notification) {
        // as we send to a general topic, we filter client side by individual notification settings
        notification.getTutorialGroup().getRegistrations().stream().map(TutorialGroupRegistration::getStudent)
                .forEach(user -> websocketMessagingService.sendMessage(notification.getTopic(user.getId()), notification));
        websocketMessagingService.sendMessage(notification.getTopic(notification.getTutorialGroup().getTeachingAssistant().getId()), notification);
    }

    private Set<User> findUsersToNotify(TutorialGroupNotification notification, boolean notifyTutor) {
        TutorialGroupRegistrationApi api = tutorialGroupRegistrationApi.orElseThrow(() -> new ApiNotPresentException(TutorialGroupRegistrationApi.class, PROFILE_CORE));

        var tutorialGroup = notification.getTutorialGroup();
        // ToDo: Adapt to the type of registration in the future
        var potentiallyInterestedUsers = api.findAllByTutorialGroupAndType(tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION).stream()
                .map(TutorialGroupRegistration::getStudent);
        if (tutorialGroup.getTeachingAssistant() != null && notifyTutor) {
            potentiallyInterestedUsers = Stream.concat(potentiallyInterestedUsers, Stream.of(tutorialGroup.getTeachingAssistant()));
        }
        return potentiallyInterestedUsers.filter(user -> StringUtils.hasText(user.getEmail())).collect(Collectors.toSet());
    }
}
