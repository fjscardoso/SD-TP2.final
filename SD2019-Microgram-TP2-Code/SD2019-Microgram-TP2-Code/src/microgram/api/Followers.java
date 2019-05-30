package microgram.api;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

public class Followers {

    String followerId;
    String userId;

    public Followers(){}

    @BsonCreator
    public Followers(@BsonProperty("followerId") String followerId, @BsonProperty("userId") String userId){
        this.followerId = followerId;
        this.userId = userId;
    }

    public String getFollowerId() {
        return followerId;
    }

    public void setFollowerId(String followerId) {
        this.followerId = followerId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
