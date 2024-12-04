package reporters;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.IdFactory;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.Severity;

import javax.inject.Inject;

/**
 * This is a simple, standard Gradle plugin that is applied to a project.
 */
public class StandardPlugin implements Plugin<Project> {

    public static final ProblemGroup PROBLEM_GROUP = IdFactory.instance().createRootProblemGroup("sample-group", "Sample Group");

    private final Problems problems;

    @Inject
    public StandardPlugin(Problems problems) {
        this.problems = problems;
    }

    @Override
    public void apply(Project project) {
        project.getTasks().register("myFailingTask", FailingTask.class);
        // tag::problems-api-report[]
        problems.getReporter().reporting(problem -> problem
                .id(IdFactory.instance().createProblemId("adhoc-plugin-deprecation", "Plugin is deprecated", PROBLEM_GROUP))
                .contextualLabel("The 'standard-plugin' is deprecated")
                .documentedAt("https://github.com/gradle/gradle/README.md")
                .severity(Severity.WARNING)
                .solution("Please use a more recent plugin version")
        );
        // end::problems-api-report[]
    }
}
