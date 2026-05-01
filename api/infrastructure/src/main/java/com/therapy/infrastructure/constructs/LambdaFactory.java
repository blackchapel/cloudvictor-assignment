package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.Map;

/**
 * Factory helper that creates Lambda functions with consistent settings.
 * Abstracts repeated configuration (runtime, memory, timeout, code path, env vars)
 * so the stack stays DRY and changes propagate uniformly.
 */
public class LambdaFactory {

    private static final String JAR_PATH = "../application/target/therapy-api-application.jar";
    private static final Runtime RUNTIME = Runtime.JAVA_11;
    private static final int MEMORY_MB = 512;
    private static final int TIMEOUT_SECONDS = 30;

    private final Construct scope;
    private final Map<String, String> commonEnv;

    public LambdaFactory(Construct scope, Map<String, String> commonEnv) {
        this.scope = scope;
        this.commonEnv = commonEnv;
    }

    /**
     * Create a single Lambda function.
     *
     * @param id          CDK logical ID (unique within the stack)
     * @param handlerFqn  Fully-qualified handler class name, e.g.
     *                    "com.therapy.handler.session.CreateSessionHandler"
     * @param extraEnv    Handler-specific environment variables (merged on top of commonEnv)
     */
    public Function create(String id, String handlerFqn, Map<String, String> extraEnv) {
        Map<String, String> env = new java.util.HashMap<>(commonEnv);
        if (extraEnv != null) env.putAll(extraEnv);

        return Function.Builder.create(scope, id)
                .runtime(RUNTIME)
                .handler(handlerFqn)
                .code(Code.fromAsset(JAR_PATH))
                .memorySize(MEMORY_MB)
                .timeout(Duration.seconds(TIMEOUT_SECONDS))
                .environment(env)
                .logRetention(RetentionDays.ONE_WEEK)
                .build();
    }

    /** Convenience overload when no extra env vars are needed. */
    public Function create(String id, String handlerFqn) {
        return create(id, handlerFqn, null);
    }
}
