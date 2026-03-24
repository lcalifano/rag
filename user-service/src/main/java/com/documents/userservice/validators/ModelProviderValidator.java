package com.documents.userservice.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ModelProviderValidatorClass.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelProviderValidator {
    String message() default "Provider non valido. Valori accettati: OPENAI, OLLAMA, ANTHROPIC";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
