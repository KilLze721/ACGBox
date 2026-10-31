package org.killze.acgbox.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.math.BigDecimal;

/**
 * 可空的 0 到 10 整数评分，按数值判断，允许 8.0 等无实际小数部分的值。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IntegerScore.ScoreValidator.class)
public @interface IntegerScore {
    String message() default "评分必须为 0 到 10 的整数";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    /** 校验原始数值，不截断、不四舍五入，也不修改传入评分。 */
    class ScoreValidator implements ConstraintValidator<IntegerScore, BigDecimal> {
        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            return value == null || (value.compareTo(BigDecimal.ZERO) >= 0
                    && value.compareTo(BigDecimal.TEN) <= 0 && value.stripTrailingZeros().scale() <= 0);
        }
    }
}
