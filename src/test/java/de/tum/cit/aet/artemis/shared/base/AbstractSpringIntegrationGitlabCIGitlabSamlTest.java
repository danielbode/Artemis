package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SAML2;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.PipelineStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.connector.GitlabRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.gitlab.GitLabService;
import de.tum.cit.aet.artemis.programming.service.gitlabci.GitLabCIService;
import de.tum.cit.aet.artemis.programming.service.gitlabci.GitLabCITriggerService;

// TODO: rewrite this test to use LocalVC instead of GitLab
@ResourceLock("AbstractSpringIntegrationGitlabCIGitlabSamlTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_ATLAS, "gitlabci", "gitlab", PROFILE_SAML2, PROFILE_SCHEDULING, PROFILE_LTI })
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_gitlabci" })
public abstract class AbstractSpringIntegrationGitlabCIGitlabSamlTest extends AbstractArtemisIntegrationTest {

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @MockitoSpyBean
    protected GitLabCIService continuousIntegrationService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @MockitoSpyBean
    protected GitLabCITriggerService continuousIntegrationTriggerService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @MockitoSpyBean
    protected GitLabService versionControlService;

    @MockitoSpyBean
    protected GitLabApi gitlab;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    protected GitlabRequestMockProvider gitlabRequestMockProvider;

    // NOTE: this has to be a MockitoBean, because the class cannot be instantiated in the tests
    @MockitoBean
    protected RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @AfterEach
    void reset() {
        Mockito.reset(continuousIntegrationService, versionControlService, relyingPartyRegistrationRepository, mailService, gitlab);
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject, boolean useCustomBuildPlanDefinition, boolean useCustomBuildPlanWorked)
            throws Exception {
        final var exerciseRepoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = exercise.generateRepositoryName(RepositoryType.TESTS);
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCreateProjectForExercise(exercise);
        gitlabRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();

        if (failToCreateCiProject) {
            doThrow(new ContinuousIntegrationException()).when(continuousIntegrationService).createProjectForExercise(any());
            doThrow(new ContinuousIntegrationException()).when(continuousIntegrationService).createBuildPlanForExercise(any(), any(), any(), any(), any());
        }

        // saml2-specific mocks
        doReturn(null).when(relyingPartyRegistrationRepository).findByRegistrationId(anyString());
        doNothing().when(mailService).sendSAML2SetPasswordMail(any(User.class));
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos)
            throws Exception {
        mockImportRepositories(exerciseToBeImported);
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) throws Exception {
        mockConnectorRequestsForSetup(exerciseForImport, false, false, false);
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception {
        mockImportRepositories(exerciseToBeImported);
    }

    private void mockImportRepositories(ProgrammingExercise exerciseToBeImported) throws GitLabApiException {
        final var targetTemplateRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TEMPLATE);
        final var targetSolutionRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.SOLUTION);
        final var targetTestsRepoName = exerciseToBeImported.generateRepositoryName(RepositoryType.TESTS);

        gitlabRequestMockProvider.mockCheckIfProjectExists(exerciseToBeImported, false);

        gitlabRequestMockProvider.mockCreateProjectForExercise(exerciseToBeImported);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetTemplateRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetSolutionRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exerciseToBeImported, targetTestsRepoName);
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws GitLabApiException {
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws GitLabApiException {
        // Step 1a)
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        // Step 1c)
        gitlabRequestMockProvider.mockConfigureRepository(exercise, users, ltiUserExists);
        // Step 1c)
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        // Step 2 is not needed in the GitLab CI setup.
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        // Step 2 is not needed in the GitLab CI setup.
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws GitLabApiException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        gitlabRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, firstStudent.getLogin());
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws GitLabApiException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        final var repositoryPath = exercise.getProjectKey() + "/" + repositorySlug;
        gitlabRequestMockProvider.mockAddMemberToRepository(repositoryPath, newStudent.getLogin(), !status.is2xxSuccessful());
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws GitLabApiException {
        final var repositorySlug = (exercise.getProjectKey() + "-" + student.getParticipantIdentifier()).toLowerCase();
        final var repositoryPath = exercise.getProjectKey() + "/" + repositorySlug;
        gitlabRequestMockProvider.mockAddMemberToRepository(repositoryPath, student.getLogin(), !status.is2xxSuccessful());
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        // Not necessary for the core functionality
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateVcsUser(user, failInVcs);
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateCoursePermissions(updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockDeleteRepository(projectKey + "/" + repositoryName, shouldFail);
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockDeleteProject(projectKey, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(), Set.of(group), false);
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws GitLabApiException {
        gitlabRequestMockProvider.mockUpdateVcsUser(user.getLogin(), user, Set.of(group), Set.of(), false);
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockGetBuildPlanConfig(String projectKey, String planName) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws URISyntaxException, JsonProcessingException {
        gitlabRequestMockProvider.mockHealth(isRunning ? "ok" : "notok", httpStatus);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);
    }

    public void mockAddBuildPlanToGitLabRepositoryConfiguration(boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetProject(shouldFail);
        gitlabRequestMockProvider.mockUpdateProject(shouldFail);
        gitlabRequestMockProvider.mockCreateProjectAccessToken(shouldFail);
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws GitLabApiException {
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, existsInVcs);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockRepositoryUriIsValid(VcsRepositoryUri repositoryUri, String projectKey, boolean isUrlValid) throws GitLabApiException {
        gitlabRequestMockProvider.mockRepositoryUriIsValid(repositoryUri, isUrlValid);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String buildPlanId, boolean buildPlanExists, boolean shouldFail) {
        // Unsupported action in GitLab CI setup
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws GitLabApiException {
        mockTriggerBuild(false);
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation programmingExerciseParticipation) throws GitLabApiException {
        mockTriggerBuild(true);
    }

    private void mockTriggerBuild(boolean shouldFail) throws GitLabApiException {
        gitlabRequestMockProvider.mockCreateTrigger(shouldFail);
        gitlabRequestMockProvider.mockTriggerPipeline(shouldFail);
        gitlabRequestMockProvider.mockDeleteTrigger(shouldFail);
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUri repositoryUri, String projectKey, Set<User> users) throws GitLabApiException {
        gitlabRequestMockProvider.setRepositoryPermissionsToReadOnly(repositoryUri, users);
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) throws GitLabApiException {
        gitlabRequestMockProvider.mockConfigureRepository(exercise, students, userExists);
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
    }

    public void mockGetBuildStatus(PipelineStatus pipelineStatus) throws GitLabApiException {
        gitlabRequestMockProvider.mockGetBuildStatus(pipelineStatus);
    }

    @Override
    public void resetMockProvider() throws Exception {
        gitlabRequestMockProvider.reset();
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) {
        // Not needed here.
    }

    @Override
    public void verifyMocks() {
        gitlabRequestMockProvider.verifyMocks();
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        gitlabRequestMockProvider.mockUserExists(username, true);
    }

    @Override
    public void mockGetCiProjectMissing(ProgrammingExercise exercise) throws IOException {
        // not needed for GitLab
    }
}
