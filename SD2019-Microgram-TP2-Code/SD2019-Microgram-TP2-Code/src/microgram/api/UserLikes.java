package microgram.api;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

public class UserLikes {

    String userId;
    String postId;

    public UserLikes() {
    }

    @BsonCreator
    public UserLikes(@BsonProperty("userId") String userId, @BsonProperty("postId") String postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }
}