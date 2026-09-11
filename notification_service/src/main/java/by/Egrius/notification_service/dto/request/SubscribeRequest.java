package by.Egrius.notification_service.dto.request;

// May be a trouble, we should be sure that user is authenticated and that's really his request
// So better add jwt token filter with public key like we have in other services
public record SubscribeRequest (
        String userId
) { }
