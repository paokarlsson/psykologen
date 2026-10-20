package se.olaslab.psykologen;

import static spola.test.Tests.suite;

import java.util.List;

import se.olaslab.psykologen.auth.AuthenticationTest;
import se.olaslab.psykologen.context.ContextStrategyTest;
import se.olaslab.psykologen.controller.PsykologenControllerTest;
import se.olaslab.psykologen.intervention.InterventionsTest;
import se.olaslab.psykologen.prompt.BulletLinesTest;
import se.olaslab.psykologen.prompt.PromptTemplatesTest;
import se.olaslab.psykologen.service.ai.AiClientTest;
import se.olaslab.psykologen.session.ConversationSessionTest;
import se.olaslab.psykologen.storage.StorageCompatibilityTest;
import se.olaslab.psykologen.trace.TraceRecorderTest;
import spola.test.Runner;

/** Kör alla tester: {@code java Build.java}. */
public final class AllTests {

    private AllTests() {
    }

    public static void main(String[] args) {
        var report = Runner.run(suite("psykologen", List.of(
                ConfigurationTest.tests(),
                AuthenticationTest.tests(),
                PsykologenControllerTest.tests(),
                AiClientTest.tests(),
                StorageCompatibilityTest.tests(),
                ContextStrategyTest.tests(),
                InterventionsTest.tests(),
                BulletLinesTest.tests(),
                PromptTemplatesTest.tests(),
                ConversationSessionTest.tests(),
                TraceRecorderTest.tests())), System.out);
        System.exit(report.success() ? 0 : 1);
    }
}
