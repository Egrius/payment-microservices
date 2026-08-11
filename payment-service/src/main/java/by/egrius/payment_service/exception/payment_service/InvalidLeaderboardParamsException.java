package by.egrius.payment_service.exception.payment_service;

public class InvalidLeaderboardParamsException extends PaymentServiceException {
    private final Integer leaderboardLimit;
    private final Integer daysCount;

    public InvalidLeaderboardParamsException(Integer leaderboardLimit, Integer daysCount) {
        super(String.format("Invalid leaderboard parameters: limit=%s, days=%s",
                leaderboardLimit, daysCount));
        this.leaderboardLimit = leaderboardLimit;
        this.daysCount = daysCount;
    }


    public InvalidLeaderboardParamsException(String message, Integer leaderboardLimit, Integer daysCount) {
        super(message);
        this.leaderboardLimit = leaderboardLimit;
        this.daysCount = daysCount;
    }

    public Integer getLeaderboardLimit() {
        return leaderboardLimit;
    }

    public Integer getDaysCount() {
        return daysCount;
    }
}