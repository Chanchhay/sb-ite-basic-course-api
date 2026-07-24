package kh.edu.istad.ite.config.filter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchRequestDto {
    String column;
    String value;
    Operation operation;
    String joinTable;

    public enum Operation {
        //        EQUAL, LIKE, IN, GREATER_THAN, LESS_THAN, BETWEEN, JOIN;
        EQUAL,
        NOT_EQUAL,

        LIKE,
        NOT_LIKE,
        STARTS_WITH,
        ENDS_WITH,

        IN,
        NOT_IN,

        GREATER_THAN,
        GREATER_THAN_EQUAL,
        LESS_THAN,
        LESS_THAN_EQUAL,

        BETWEEN,
        NOT_BETWEEN,

        IS_NULL,
        IS_NOT_NULL,

        TRUE,
        FALSE,

        IS_FALSE, JOIN
    }
}
