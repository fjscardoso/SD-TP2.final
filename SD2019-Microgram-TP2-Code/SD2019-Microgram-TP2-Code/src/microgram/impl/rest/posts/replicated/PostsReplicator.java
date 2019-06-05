package microgram.impl.rest.posts.replicated;
import microgram.api.Post;
import microgram.api.java.Posts;
import microgram.api.java.Result;
import microgram.impl.rest.replication.MicrogramOperation;
import microgram.impl.rest.replication.MicrogramOperationExecutor;
import microgram.impl.rest.replication.OrderedExecutor;

import java.util.List;

import static microgram.api.java.Result.ErrorCode.NOT_IMPLEMENTED;
import static microgram.api.java.Result.error;
import static microgram.impl.rest.replication.MicrogramOperation.Operation.*;

public class PostsReplicator implements MicrogramOperationExecutor, Posts {

	private static final int PostID = 0, UserID = 1;
	
	final Posts localReplicaDB;
	final OrderedExecutor executor;
	
	PostsReplicator(Posts localDB, OrderedExecutor executor) {
		this.localReplicaDB = localDB;
		this.executor = executor.init(this);
	}


	@Override
	public Result<Post> getPost(String postId) {
		return executor.replicate( new MicrogramOperation(GetPost, postId));
	}

	@Override
	public Result<String> createPost(Post post) {
		return executor.replicate( new MicrogramOperation(CreatePost, post));
	}

	@Override
	public Result<Void> deletePost(String postId) {
		return executor.replicate( new MicrogramOperation(DeletePost, postId));
	}

	@Override
	public Result<Void> like(String postId, String userId, boolean isLiked) {
		String[] x = new String[2];
		x[PostID] = postId;
		x[UserID] = userId;
		if(isLiked)
			return executor.replicate( new MicrogramOperation(LikePost, x));
		else
			return executor.replicate( new MicrogramOperation(UnLikePost, x));
	}

	@Override
	public Result<Boolean> isLiked(String postId, String userId) {
		String[] x = new String[2];
		x[PostID] = postId;
		x[UserID] = userId;
		return executor.replicate( new MicrogramOperation(IsLiked, x));
	}

	@Override
	public Result<List<String>> getPosts(String userId) {
		return executor.replicate( new MicrogramOperation(GetPosts, userId));
	}

	@Override
	public Result<List<String>> getFeed(String userId) {
		return executor.replicate( new MicrogramOperation(GetFeed, userId));
	}

	@Override
	public Result<?> execute( MicrogramOperation op ) {
		switch( op.type ) {
			case CreatePost: {
				return localReplicaDB.createPost( op.arg( Post.class));
			}
			case GetPost: {
				return localReplicaDB.getPost(op.arg(String.class));
			}
			case DeletePost: {
				return localReplicaDB.deletePost(op.arg(String.class));
			}
			case LikePost: {
				String[] s = op.args(String[].class);
				return localReplicaDB.like(s[PostID], s[UserID], true);

			}
			case UnLikePost: {
				String[] s = op.args(String[].class);
				return localReplicaDB.like(s[PostID], s[UserID], false);
			}
			case IsLiked: {
				String[] s = op.args(String[].class);
				return localReplicaDB.isLiked(s[PostID], s[UserID]);
			}
			case GetPosts: {
				String[] users = op.args(String[].class);
				return localReplicaDB.getPosts(op.arg(String.class));
			}
			case GetFeed: {
				String[] users = op.args(String[].class);
				return localReplicaDB.getFeed(op.arg(String.class));
			}
			default:
				return error(NOT_IMPLEMENTED);
		}
	}
}
