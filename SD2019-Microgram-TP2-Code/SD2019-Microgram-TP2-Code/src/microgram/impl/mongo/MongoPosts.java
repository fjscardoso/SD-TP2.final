package microgram.impl.mongo;

import microgram.api.Followers;
import microgram.api.Profile;
import microgram.api.java.Posts;

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
import microgram.api.Post;
import microgram.api.UserLikes;
import microgram.api.java.Result;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import utils.Hash;

import java.util.*;

import static microgram.api.java.Result.ErrorCode.CONFLICT;
import static microgram.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static microgram.api.java.Result.error;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoPosts implements Posts {

    private MongoDatabase dbName;

    private MongoCollection<Post> dbPosts;

    private MongoCollection<UserLikes> dbLikes;

    private MongoCollection<Followers> dbFollowers;

    public MongoPosts() {

        MongoClient mongo = new MongoClient("mongo1");

        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        dbName = mongo.getDatabase("Database").withCodecRegistry(pojoCodecRegistry);

        dbName.drop();

        dbPosts = dbName.getCollection("dbPosts", Post.class);

        dbLikes = dbName.getCollection("dbLikes", UserLikes.class);

        dbFollowers = dbName.getCollection("dbFollowers", Followers.class);

        dbPosts.createIndex(Indexes.ascending("postId"), new IndexOptions().unique(true));
        dbLikes.createIndex(Indexes.hashed("userId"));
        dbLikes.createIndex(Indexes.ascending("userId", "postId"), new IndexOptions().unique(true));
    }

    @Override
    public Result<Post> getPost(String postId) {
        try {
            MongoCursor<Post> i = dbPosts.find(Filters.eq("postId", postId)).iterator();

            if(!i.hasNext())
                return error(Result.ErrorCode.NOT_FOUND);

            Post p = i.next();
            p.setLikes((int) dbLikes.countDocuments(Filters.eq("postId", postId)));
            return Result.ok(p);

        } catch(MongoException e){
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deletePost(String postId) {
        try {
            MongoCursor<Post> i = dbPosts.find(Filters.eq("postId", postId)).iterator();

            if(!i.hasNext())
                return error(Result.ErrorCode.NOT_FOUND);

            dbPosts.deleteOne(Filters.eq("postId", postId));

            DeleteResult res = dbPosts.deleteMany(Filters.eq("postId", postId));
            dbLikes.deleteMany(Filters.eq("postId", postId));
            //if(res.getDeletedCount() == 0)
            //    return error(Result.ErrorCode.NOT_FOUND);

            System.out.printf("Deleted: %d documents...\n", res.getDeletedCount());

            return Result.ok();
        } catch (MongoException e) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }

    }

    @Override
    public Result<String> createPost(Post p) {
        try {

            String postId = Hash.of(p.getOwnerId(), p.getMediaUrl());

            MongoCursor<Post> i = dbPosts.find(Filters.eq("postId", postId)).iterator();

            if(i.hasNext())
                return error(CONFLICT);

            //Post pojo = new Post(postId, p.getOwnerId(), p.getMediaUrl(), p.getLocation(),0,0);
            dbPosts.insertOne(new Post(postId, p.getOwnerId(), p.getMediaUrl(), p.getLocation(),p.getTimestamp()));

            return Result.ok(postId);

        } catch( MongoWriteException e ) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> like(String postId, String userId, boolean isLiked) {

        try {
            MongoCursor<Post> it = dbPosts.find(Filters.eq("postId", postId)).iterator();

            if (!it.hasNext())
                return error(Result.ErrorCode.NOT_FOUND);

            if (isLiked) {
                if (isLiked(postId, userId).value())
                    return error(CONFLICT);
                else
                    dbLikes.insertOne(new UserLikes(userId, postId));
            }
            else{
                if(isLiked(postId, userId).value())
                    dbLikes.deleteOne(Filters.and(Filters.eq("userId", userId), Filters.eq("postId", postId)));
                else
                    return error(Result.ErrorCode.NOT_FOUND);


            }

            return Result.ok();

        }catch(MongoException e){
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }

    }

    @Override
    public Result<Boolean> isLiked(String postId, String userId) {
        MongoCursor<UserLikes> i = dbLikes.find(Filters.and(Filters.eq("userId", userId), Filters.eq("postId", postId))).iterator();
        return Result.ok(i.hasNext());
    }

    @Override
    public Result<List<String>> getPosts(String userId) {
        List<String> list = new LinkedList<String>();
        MongoCursor<Post> i = dbPosts.find(Filters.eq("ownerId", userId)).iterator();
        while(i.hasNext())
            list.add(i.next().getPostId());

        return Result.ok(list);
    }

    @Override
    public Result<List<String>> getFeed(String userId) {

        List<String> list = new LinkedList<String>();

        MongoCursor<Followers> i = dbFollowers.find(Filters.eq("followerId", userId)).iterator();

        while(i.hasNext()){
            list.addAll(getPosts(i.next().getUserId()).value());
        }

        return Result.ok(list);
    }

}
