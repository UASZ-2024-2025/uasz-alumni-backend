package com.uasz.alumni.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Garde-fou de câblage : un {@code @Async("x")} sans bean « x » ne casse qu'au runtime
 * (NoSuchBeanDefinitionException), et aucun test unitaire ne l'attrape.
 */
class AsyncExecutorsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AppConfig.class);

    @Test
    void mailExecutorBeanExists() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("mailExecutor");
            assertThat(context.getBean("mailExecutor")).isInstanceOf(Executor.class);
        });
    }

    @Test
    void everyNamedAsyncQualifierInTheApplicationPointsToAnExecutorBean() throws Exception {
        Set<String> qualifiers = namedAsyncQualifiers("com.uasz.alumni.user");

        // si ce contrôle tombe, le scan ne trouve plus rien et le test ne protège plus rien
        assertThat(qualifiers).contains("mailExecutor");

        runner.run(context -> {
            for (String qualifier : qualifiers) {
                assertThat(context).as("bean pour @Async(\"" + qualifier + "\")").hasBean(qualifier);
                assertThat(context.getBean(qualifier)).isInstanceOf(Executor.class);
            }
        });
    }

    private static Set<String> namedAsyncQualifiers(String basePackage) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class)); // couvre @Service, @Configuration...
        Set<String> qualifiers = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
            Class<?> type = ClassUtils.forName(definition.getBeanClassName(), null);
            for (Method method : type.getDeclaredMethods()) {
                Async async = AnnotatedElementUtils.findMergedAnnotation(method, Async.class);
                if (async != null && !async.value().isBlank()) {
                    qualifiers.add(async.value());
                }
            }
        }
        return qualifiers;
    }
}
