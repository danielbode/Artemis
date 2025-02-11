package de.tum.cit.aet.artemis.programming.service.localvc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

/**
 * Represents a URI for a local version control (VC) repository. This class extends {@link VcsRepositoryUri} by adding specific properties
 * and methods suited for local VC repositories, such as handling project keys, repository slugs, and distinguishing between regular and practice repositories.
 * It constructs URIs using a base URL, typically provided by an environment variable, combined with a standardized repository path format.
 */
public class LocalVCRepositoryUri extends VcsRepositoryUri {

    /** The project key under which the repository is categorized in the local VC system. */
    private final String projectKey;

    /** The slug or identifier for the repository, unique within its project. */
    private final String repositorySlug;

    /** The repository type or username associated with the repository, derived from the slug or project settings. */
    private final String repositoryTypeOrUserName;

    /** A flag indicating whether the repository is designated as a practice repository. */
    private final boolean isPracticeRepository;

    /**
     * Constructs a {@link LocalVCRepositoryUri} instance using the specified project key and repository slug.
     * The URI is generated by concatenating the base URL of the local VC server with the result of the {@code buildRepositoryPath} method.
     * This constructor initializes the repository URI, and determines whether it represents a practice repository and extracts the repository type or user name.
     *
     * @param projectKey     The project key that identifies the group or project under which the repository is categorized in the local VC system.
     * @param repositorySlug The slug or name of the repository, typically used as a unique identifier for the repository within the project.
     * @param localVCBaseUrl The base URL of the local version control server, provided as a {@link URL}, which forms the prefix of the full repository URI.
     * @throws LocalVCInternalException If the project key or repository slug results in an invalid URI, encapsulating the {@link URISyntaxException}.
     */
    public LocalVCRepositoryUri(String projectKey, String repositorySlug, URL localVCBaseUrl) {
        final String urlString = localVCBaseUrl + buildRepositoryPath(projectKey, repositorySlug);
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCInternalException("Could not create local VC Repository URI", e);
        }

        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
        this.repositoryTypeOrUserName = getRepositoryTypeOrUserName(repositorySlug, projectKey);
        this.isPracticeRepository = isPracticeRepository(repositorySlug, projectKey);
    }

    /**
     * Constructs a {@link LocalVCRepositoryUri} instance directly from a full URL string.
     * This constructor parses the given URL string to initialize the URI and extract components such as the project key and repository slug.
     * The URL string must already include the base URL; the constructor performs no concatenation but directly parses and validates the provided string.
     * <p>
     * The constructor analyzes the path segment of the URL to extract the project key and repository slug, assuming a specific structure:
     * The project key is expected to be the segment immediately following the first "git" directory in the path, and the repository slug follows the project key.
     * The ".git" suffix, if present, is removed from the repository slug.
     *
     * @param urlString The entire URL string representing the complete URI of the repository. It must be a well-formed URL that includes the base URL.
     * @throws LocalVCInternalException If the URL string does not form a valid URI or if the expected segments ('git', project key, repository slug) are not found or invalid.
     */
    public LocalVCRepositoryUri(String urlString) {
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCInternalException("Could not create local VC Repository URI", e);
        }

        var urlPath = Path.of(this.uri.getPath());

        // Find index of "git" in the path. This is needed in case the base URL contains a path.
        final var startIndex = getGitPartStartIndex(urlString, urlPath);

        this.projectKey = urlPath.getName(startIndex + 1).toString();
        this.repositorySlug = urlPath.getName(startIndex + 2).toString().replace(".git", "");
        validateProjectKeyAndRepositorySlug(projectKey, repositorySlug);

        this.repositoryTypeOrUserName = getRepositoryTypeOrUserName(repositorySlug, projectKey);
        this.isPracticeRepository = isPracticeRepository(repositorySlug, projectKey);
    }

    /**
     * Determines the index of the "git" segment within a given URL path.
     * This method scans the path segments of a URL to find the segment "git", which typically marks the start of
     * repository-specific segments in a URL structure used for version control systems. This index is critical for
     * further processing to extract project key and repository slug.
     * <p>
     * The method performs several validations:
     * <ol>
     * <li>Ensures that the "git" segment is present.</li>
     * <li>Checks that there are at least two more segments following "git" to accommodate a typical URL format of "git/{projectKey}/{repositorySlug}.git".</li>
     * <li>Verifies that the repository slug (third segment after "git") ends with ".git".</li>
     * </ol>
     *
     * Examples:
     * <ul>
     * <li>Correct URL: "https://artemis.tum.de/git/projectKey/repositorySlug.git" - Returns 1 as "git" is at index 1.</li>
     * <li>Incorrect URL: "https://artemis.tum.de/projectKey/repositorySlug.git" - Throws LocalVCInternalException because the "git" segment is missing.</li>
     * <li>Incorrect URL: "https://artemis.tum.de/git/projectKey" - Throws LocalVCInternalException because there are not enough segments after "git".</li>
     * <li>Incorrect URL: "https://artemis.tum.de/git/projectKey/repositorySlug" - Throws LocalVCInternalException because the repository slug does not end with ".git".</li>
     * </ul>
     *
     * @param urlString The full URL string being analyzed, provided for context in error messages.
     * @param urlPath   The path component of the URL, parsed into segments.
     * @return The index of the "git" segment within the path if valid.
     * @throws LocalVCInternalException If the "git" segment is missing, if there are insufficient segments after "git",
     *                                      or if the repository slug does not end with ".git".
     */
    private static int getGitPartStartIndex(String urlString, Path urlPath) throws LocalVCInternalException {
        var startIndex = -1;
        for (int i = 0; i < urlPath.getNameCount(); i++) {
            if ("git".equals(urlPath.getName(i).toString())) {
                startIndex = i;
                break;
            }
        }

        if (startIndex == -1) {
            throw new LocalVCInternalException("Invalid local VC Repository URI: 'git' directory not found in the URL: " + urlString);
        }
        if (urlPath.getNameCount() < startIndex + 3) {
            throw new LocalVCInternalException("Invalid local VC Repository URI: URL does not contain enough segments after 'git' to form a valid repository path: " + urlString);
        }
        if (!urlPath.getName(startIndex + 2).toString().endsWith(".git")) {
            throw new LocalVCInternalException(
                    "Invalid local VC Repository URI: Repository slug segment '" + urlPath.getName(startIndex + 2) + "' does not end with '.git' in the URL: " + urlString);
        }
        return startIndex;
    }

    /**
     * Constructs a {@link LocalVCRepositoryUri} instance from a specified repository path and a base URL of the local VC server.
     * This constructor adapts to both local checked out repository paths (which end with ".git") and paths representing remote repositories.
     * If a local repository path is provided, it strips the ".git" suffix and uses the parent directory to derive the project key and repository slug.
     * <p>
     * The constructed URI combines the local VC server's base URL with the built repository path derived from the provided path's segments.
     *
     * <p>
     * Example usage and outputs:
     * </p>
     * <ul>
     * <li>
     * Input: Local repository path - {@code Path.of("/local/path/projectX/my-repo/.git")}
     * and Local VC server URL - {@code new URI("https://artemis.tum.de").getURL()}
     * Output: {@code https://artemis.tum.de/git/projectX/my-repo.git}
     * </li>
     * <li>
     * Input: Remote repository path - {@code Path.of("/remote/path/projectY/my-repo")}
     * and Local VC server URL - {@code new URI("https://artemis.tum.de").getURL()}
     * Output: {@code https://artemis.tum.de/git/projectY/my-repo.git}
     * </li>
     * </ul>
     *
     * @param repositoryPath   The path to the repository. It can be a path to a local checked-out repository (ending with ".git") or a more general path to a repository in a
     *                             "local-vcs-repos" folder.
     * @param localVCServerUrl The base URL of the local VC server, typically defined in an environment variable. This URL is used as the prefix in constructing the full repository
     *                             URI.
     * @throws LocalVCInternalException If the repository path is invalid or if the URI construction fails due to an invalid URL format.
     */
    public LocalVCRepositoryUri(Path repositoryPath, URL localVCServerUrl) {
        if (".git".equals(repositoryPath.getFileName().toString())) {
            // This is the case when a local repository path is passed instead of a path to a remote repository in the "local-vcs-repos" folder.
            // In this case we remove the ".git" suffix.
            repositoryPath = repositoryPath.getParent();
        }

        this.projectKey = repositoryPath.getParent().getFileName().toString();
        this.repositorySlug = repositoryPath.getFileName().toString().replace(".git", "");
        validateProjectKeyAndRepositorySlug(projectKey, repositorySlug);

        final String urlString = localVCServerUrl + buildRepositoryPath(projectKey, repositorySlug);
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCInternalException("Could not create local VC Repository URI", e);
        }

        this.repositoryTypeOrUserName = getRepositoryTypeOrUserName(repositorySlug, projectKey);
        this.isPracticeRepository = isPracticeRepository(repositorySlug, projectKey);
    }

    /**
     * Constructs a path for the repository URI using the provided project key and repository slug.
     * This path is structured to conform to a standard repository URL format within a version control system.
     *
     * @param projectKey     The project key under which the repository is categorized.
     * @param repositorySlug The name of the repository or slug, typically a unique identifier within the project.
     * @return The constructed repository path in the format "/git/{projectKey}/{repositorySlug}.git".
     */
    private String buildRepositoryPath(String projectKey, String repositorySlug) {
        return "/git/" + projectKey + "/" + repositorySlug + ".git";
    }

    /**
     * Extracts the repository type or username from a repository slug by removing the project key and any "practice-" prefix.
     * This method is used to normalize the repository slug to obtain a clean identifier that can be used in contexts where the full slug is not needed.
     *
     * @param repositorySlug The slug or name of the repository from which to extract the type or username.
     * @param projectKey     The project key associated with the repository to aid in the extraction process.
     * @return The normalized repository type or username, free of the project key prefix and "practice-" designation.
     */
    private String getRepositoryTypeOrUserName(String repositorySlug, String projectKey) {
        String repositoryTypeOrUserNameWithPracticePrefix = repositorySlug.toLowerCase().replace(projectKey.toLowerCase() + "-", "");
        return repositoryTypeOrUserNameWithPracticePrefix.replace("practice-", "");
    }

    /**
     * Determines whether the repository is designated as a practice repository based on its slug.
     * A repository is considered a practice repository if its slug starts with the project key followed by "-practice-".
     *
     * @param repositorySlug The slug or name of the repository to be evaluated.
     * @param projectKey     The project key associated with the repository, used to check the slug prefix.
     * @return true if the repository is a practice repository, false otherwise.
     */
    private boolean isPracticeRepository(String repositorySlug, String projectKey) {
        return repositorySlug.toLowerCase().startsWith(projectKey.toLowerCase() + "-practice-");
    }

    /**
     * Validates that the repository slug begins with the corresponding project key, ensuring that the repository is correctly categorized under the project.
     * This method throws an exception if the validation fails, indicating a mismatch between the project key and repository slug.
     *
     * @param projectKey     The project key that should prefix the repository slug.
     * @param repositorySlug The slug or name of the repository to be validated.
     * @throws LocalVCInternalException If the repository slug does not start with the project key, indicating an incorrect or malformed slug.
     */
    private void validateProjectKeyAndRepositorySlug(String projectKey, String repositorySlug) {
        if (!repositorySlug.toLowerCase().startsWith(projectKey.toLowerCase())) {
            throw new LocalVCInternalException("Invalid project key and repository slug: " + projectKey + ", " + repositorySlug);
        }
    }

    /**
     * Retrieves the project key associated with this repository URI.
     * The project key is a part of the repository's identifying data, usually used to categorize repositories under specific projects or groups.
     *
     * @return The project key as a string.
     */
    public String getProjectKey() {
        return projectKey;
    }

    /**
     * Retrieves the repository type or username extracted from the repository slug.
     * This can be the type of the repository or a username associated with personal or user-specific repositories.
     * The value is normalized by removing the project key and any "practice-" prefix.
     *
     * @return The repository type or username as a string.
     */
    public String getRepositoryTypeOrUserName() {
        return repositoryTypeOrUserName;
    }

    /**
     * Determines whether this repository is designated as a practice repository.
     * A practice repository typically contains experimental or temporary projects and is marked specifically in the slug.
     *
     * @return true if the repository is a practice repository, false otherwise.
     */
    public boolean isPracticeRepository() {
        return isPracticeRepository;
    }

    /**
     * Computes and returns the full path to the repository stored within the local version control system.
     * This path is constructed using the base path of the local VC system, combined with the project key and repository slug.
     * The result is a Path object that represents the directory where the repository is stored or should be stored locally.
     *
     * @param localVCBasePath The base path of the local VC system, typically defined in an environment variable or a configuration setting.
     * @return The full Path to the repository, which includes the base path, project key, and repository slug with a ".git" suffix.
     */
    public Path getLocalRepositoryPath(String localVCBasePath) {
        Path relativeRepositoryPath = getRelativeRepositoryPath();
        return Path.of(localVCBasePath).resolve(relativeRepositoryPath);
    }

    /**
     * Computes and returns the path to the repository stored within the local version control system relative to the base path.
     * This path is constructed using the project key and repository slug.
     * The result is a Path object that can be resolved against the base path
     * to get the directory where the repository is stored or should be stored locally.
     *
     * @return The relative Path to the repository, which includes the project key and repository slug with a ".git" suffix.
     */
    public Path getRelativeRepositoryPath() {
        return Path.of(projectKey, repositorySlug + ".git");
    }
}
