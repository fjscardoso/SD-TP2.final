package microgram.impl.rest.media;

import microgram.api.java.Media;
import microgram.api.rest.RestMedia;
import microgram.impl.dropbox.DropboxMedia;
import microgram.impl.java.JavaMedia;
import microgram.impl.rest.RestResource;

public class RestMediaResources extends RestResource implements RestMedia {

	final Media impl;
	final String baseUri;

	public RestMediaResources(String baseUri) throws Exception{
		this.baseUri = baseUri;
		this.impl = DropboxMedia.createClientWithAccessToken();
		//this.impl = new JavaMedia();
		//impl.upload(new byte[10]);
	}

	@Override
	public String upload(byte[] bytes) {
		return baseUri + "/" + super.resultOrThrow(impl.upload(bytes));
	}

	@Override
	public byte[] download(String id) {
		return super.resultOrThrow(impl.download(id));
	}

	@Override
	public void delete(String id) {
		super.resultOrThrow(impl.delete(id));
	}
}
