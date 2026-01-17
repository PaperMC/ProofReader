package io.papermc.proofreader.proofreader;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.MapperFeature;

@Configuration
@EnableConfigurationProperties(ProofReaderConfig.Config.class)
public class ProofReaderConfig {

    @ConfigurationProperties("proofreader")
    public record Config(
            Repo sourceRepo,
            Repo targetRepo,
            String clientId,
            String installationId,
            String privateKey,
            @Nullable String buildCacheUser,
            @Nullable String buildCachePassword
    ) {

        public record Repo(String owner, String name) {}
    }

    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }
}
