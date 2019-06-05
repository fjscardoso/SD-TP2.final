package microgram.impl.mongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;
import microgram.api.Followers;
import microgram.api.Post;
import microgram.api.Profile;
import microgram.api.java.Profiles;
import microgram.api.java.Result;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.LinkedList;
import java.util.List;

import static microgram.api.java.Result.ErrorCode.CONFLICT;
import static microgram.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static microgram.api.java.Result.error;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoProfiles implements Profiles {

    private MongoDatabase dbName;

    private MongoCollection<Profile> dbProfiles;

    private MongoCollection<Followers> dbFollowers;

    private MongoCollection<Post> dbPosts;

    public MongoProfiles() {

        MongoClient mongo = new MongoClient("0.0.0.0");

        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        dbName = mongo.getDatabase("Database").withCodecRegistry(pojoCodecRegistry);

        dbProfiles = dbName.getCollection("dbProfiles", Profile.class);

        dbFollowers = dbName.getCollection("dbFollowers", Followers.class);

        dbPosts = dbName.getCollection("dbPosts", Post.class);

        dbProfiles.createIndex(Indexes.hashed("userId"));
        dbProfiles.createIndex(Indexes.ascending("userId"), new IndexOptions().unique(true));

        //dbFollowers.createIndex(Indexes.hashed("followerId"));
        //dbFollowers.createIndex(Indexes.hashed("userId"));
        dbFollowers.createIndex(Indexes.ascending("followerId", "userId"), new IndexOptions().unique(true));


    }


    @Override
    public Result<Profile> getProfile(String userId) {
        try {

            Profile p = dbProfiles.find(Filters.eq("userId", userId)).first();

            if(p == null)
                return error(Result.ErrorCode.NOT_FOUND);

            p.setFollowers((int) dbFollowers.countDocuments(Filters.eq("userId", userId)));
            p.setFollowing((int) dbFollowers.countDocuments(Filters.eq("followerId", userId)));
            p.setPosts((int) dbPosts.countDocuments(Filters.eq("ownerId", userId)));

            return Result.ok(p);

        } catch(MongoException e){
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> createProfile(Profile profile) {
        try {

            Profile p = dbProfiles.find(Filters.eq("userId", profile.getUserId())).first();

            if(p != null)
                return error(CONFLICT);

            dbProfiles.insertOne(profile);

            return Result.ok();

        } catch( MongoWriteException e ) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deleteProfile(String userId) {
        try {

            Profile p = dbProfiles.find(Filters.eq("userId", userId)).first();

            if(p == null)
                return error(Result.ErrorCode.NOT_FOUND);

            dbProfiles.deleteOne(Filters.eq("userId", userId));

            DeleteResult res = dbProfiles.deleteMany(Filters.eq("userId", userId));
            //if(res.getDeletedCount() == 0)
            //    return error(Result.ErrorCode.NOT_FOUND);

            System.out.printf("Deleted: %d documents...\n", res.getDeletedCount());

            dbFollowers.deleteMany(Filters.eq("userId", userId));
            dbFollowers.deleteMany(Filters.eq("followerId", userId));

            dbPosts.deleteMany(Filters.eq("ownerId", userId));

            return Result.ok();
        } catch (MongoException e) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<Profile>> search(String prefix) {
        List<Profile> list = new LinkedList<Profile>();

        MongoCursor<Profile> i = dbProfiles.find(Filters.regex("userId", "^" + prefix)).iterator();
        while(i.hasNext())
            list.add(i.next());

        return Result.ok(list);

    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing) {

        Profile p1 = dbProfiles.find(Filters.eq("userId", userId1)).first();
        Profile p2 = dbProfiles.find(Filters.eq("userId", userId2)).first();

        if(p1 == null || p2 == null)
            return error(Result.ErrorCode.NOT_FOUND);

        if(isFollowing){
            if(isFollowing(userId1, userId2).value())
                return Result.ok();
            else
                dbFollowers.insertOne(new Followers(userId1, userId2));
        }
        else{
            if(isFollowing(userId1, userId2).value())
                dbFollowers.deleteOne(Filters.and(Filters.eq("followerId", userId1), Filters.eq("userId", userId2)));
            else
                return Result.ok();
        }

        return Result.ok();
    }

    @Override
    public Result<Boolean> isFollowing(String userId1, String userId2) {

        Followers follower = dbFollowers.find(Filters.and(Filters.eq("followerId", userId1), Filters.eq("userId", userId2))).first();

        return Result.ok(follower != null);
    }
}
