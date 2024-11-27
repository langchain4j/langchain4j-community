package dev.langchain4j.community.model.xinference.client;

import java.util.Collections;


class AuthorizationHeaderInjector extends GenericHeaderInjector {

    AuthorizationHeaderInjector(String apiKey) {
        super(Collections.singletonMap("Authorization", "Bearer " + apiKey));
    }
}
