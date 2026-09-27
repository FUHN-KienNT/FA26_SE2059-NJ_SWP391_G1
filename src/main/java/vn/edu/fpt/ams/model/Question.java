package vn.edu.fpt.ams.model;

import java.math.BigDecimal;

public record Question(long questionId, long assignmentId, int questionOrder,
                       String questionText, BigDecimal maximumPoints, String options) {}
