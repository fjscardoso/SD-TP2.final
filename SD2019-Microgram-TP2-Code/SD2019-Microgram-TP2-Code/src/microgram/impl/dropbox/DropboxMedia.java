package microgram.impl.dropbox;

import microgram.api.java.Media;
import microgram.impl.dropbox.msgs.*;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.pac4j.scribe.builder.api.DropboxApi20;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.sun.net.httpserver.HttpServer;

import microgram.api.java.Result;
import utils.Hash;
import utils.JSON;

public class DropboxMedia implements Media{

    private static final String apiKey = "91zb24awf6gtwow";
    private static final String apiSecret = "oxc95t3bke4libw";
    private static final String accessTokenStr = "LwCMEv-XqcAAAAAAAAAANJerq57vnIy6LYWF-bDSwW6IHK6QCpWZnfMsv3KSKI3g";

    protected static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    protected static final String OCTETSTREAM_CONTENT_TYPE = "application/octet-stream";

    private static final String CREATE_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/create_folder_v2";
    private static final String LIST_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/list_folder";
    private static final String LIST_FOLDER_CONTINUE_V2_URL = "https://api.dropboxapi.com/2/files/list_folder/continue";
    private static final String CREATE_FILE_V2_URL = "https://content.dropboxapi.com/2/files/upload";
    private static final String DELETE_FILE_V2_URL = "https://api.dropboxapi.com/2/files/delete";
    private static final String DOWNLOAD_FILE_V2_URL = "https://content.dropboxapi.com/2/files/download";
    private static final String GET_TEMPORARY_LINK_FILE_V2_URL = "https://api.dropboxapi.com/2/files/get_temporary_link";

    private static final String DROPBOX_API_ARG = "Dropbox-API-Arg";

    protected OAuth20Service service;
    protected OAuth2AccessToken accessToken;

    //@param accessTokenStr String with the previously obtained access token.
    /**
     * Creates a dropbox client, given the access token.
     * @throws Exception Throws exception if something failed.
     */
    public static DropboxMedia createClientWithAccessToken() throws Exception {
        try {
            OAuth20Service service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
            OAuth2AccessToken accessToken = new OAuth2AccessToken(accessTokenStr);

            System.err.println(accessToken.getAccessToken());
            System.err.println(accessToken.toString());
            return new DropboxMedia( service, accessToken);

        } catch (Exception x) {
            x.printStackTrace();
            throw new Exception(x);
        }
    }

    protected DropboxMedia(OAuth20Service service, OAuth2AccessToken accessToken) {
        this.service = service;
        this.accessToken = accessToken;
    }


    /**
     * Create a directory in dropbox.
     *
     * @param path
     *            Path for the directory to create.
     * @return Returns a Result object.
     */
    public Result<Void> createDirectory(String path) {
        try {
            OAuthRequest createFolder = new OAuthRequest(Verb.POST, CREATE_FOLDER_V2_URL);
            createFolder.addHeader("Content-Type", JSON_CONTENT_TYPE);

            createFolder.setPayload(JSON.encode(new CreateFolderV2Args(path, false)));

            service.signRequest(accessToken, createFolder);
            Response r = service.execute(createFolder);

            if (r.getCode() == 409) {
                System.err.println("Dropbox directory already exists");
                return Result.error(Result.ErrorCode.CONFLICT);
            } else if (r.getCode() == 200) {
                System.err.println("Dropbox directory was created with success");
                return Result.ok();
            } else {
                System.err.println("Unexpected error HTTP: " + r.getCode());
                return Result.error(Result.ErrorCode.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Lists diretory at Dropbox.
     *
     * @param path
     *            Dropbix path to list.
     * @return Resturns a list of string with the contents of the directory.
     */
    public Result<List<String>> listDirectory(String path) {
        try {
            List<String> list = new ArrayList<String>();
            OAuthRequest listFolder = new OAuthRequest(Verb.POST, LIST_FOLDER_V2_URL);
            listFolder.addHeader("Content-Type", JSON_CONTENT_TYPE);
            listFolder.setPayload(JSON.encode(new ListFolderV2Args(path, true)));

            for (;;) {
                service.signRequest(accessToken, listFolder);
                Response r = service.execute(listFolder);
                if (r.getCode() != 200) {
                    System.err.println("Failed list directory: " + path + " : " + r.getMessage());
                    return Result.error(Result.ErrorCode.INTERNAL_ERROR);
                }

                ListFolderV2Return result = JSON.decode(r.getBody(), ListFolderV2Return.class);
                result.getEntries().forEach(entry -> {
                    list.add(entry.toString());
                    System.out.println(entry);
                });

                if (result.has_more()) {
                    System.err.println("continuing...");
                    listFolder = new OAuthRequest(Verb.POST, LIST_FOLDER_CONTINUE_V2_URL);
                    listFolder.addHeader("Content-Type", JSON_CONTENT_TYPE);
                    listFolder.setPayload(JSON.encode(new ListFolderContinueV2Args(result.getCursor())));
                } else
                    break;
            }
            return Result.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }


    /**
     * Write the contents of file name.
     * https://www.dropbox.com/developers/documentation/http/documentation#files-upload
     *
     * @param bytes Contents of the file.
     */
    public Result<String> upload(byte[] bytes) {
        // TODO: TO BE COMPLETED

        //O que e deve fazer return como string

        try {

            OAuthRequest uploadFile = new OAuthRequest(Verb.POST, CREATE_FILE_V2_URL);
            uploadFile.addHeader("Content-Type", OCTETSTREAM_CONTENT_TYPE);
            uploadFile.addHeader(DROPBOX_API_ARG, JSON.encode(new CreateFileV2Args("/mediastorage/" + Hash.of(bytes) + ".jpg")));
            uploadFile.setPayload(bytes);

            service.signRequest(accessToken, uploadFile);
            Response r = service.execute(uploadFile);

            System.err.println("BODY : " + r.getBody());
            System.err.println("CODE : " + r.getCode());
            System.err.println("MESSAGE : " + r.getMessage());

            if (r.getCode() == 409) {
                System.err.println("File does not exist");
                return Result.error(Result.ErrorCode.NOT_FOUND);
            } else if (r.getCode() == 200) {
                System.err.println("File uploaded");
                return Result.ok(Hash.of(bytes));
            } else {
                System.err.println("Unexpected error HTTP: " + r.getCode());
                return Result.error(Result.ErrorCode.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("rip");
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Reads the contents of file name.
     * https://www.dropbox.com/developers/documentation/http/documentation#files-download
     * https://www.dropbox.com/developers/documentation/http/documentation#files-get_temporary_link
     *
     * @param id name.
     * @return Returns the file contents.
     */
    public Result<byte[]> download(String id) {
        // TODO: TO BE COMPLETED
        try {
            OAuthRequest downloadFile = new OAuthRequest(Verb.POST, DOWNLOAD_FILE_V2_URL);
            downloadFile.addHeader("Content-Type", OCTETSTREAM_CONTENT_TYPE);
            downloadFile.addHeader(DROPBOX_API_ARG, JSON.encode(new AccessFileV2Args("/mediastorage/" + id + ".jpg")));

            service.signRequest(accessToken, downloadFile);
            Response r = service.execute(downloadFile);

            System.err.println("BODY : " + r.getBody());
            System.err.println("CODE : " + r.getCode());
            System.err.println("MESSAGE : " + r.getMessage());

            if (r.getCode() == 409) {
                System.err.println("File does not exist");
                return Result.error(Result.ErrorCode.NOT_FOUND);
            } else if (r.getCode() == 200) {
                System.err.println("File download");
                return Result.ok(r.getBody().getBytes());
            } else {
                System.err.println("Unexpected error HTTP: " + r.getCode());
                return Result.error(Result.ErrorCode.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Deletes the file name.
     * https://www.dropbox.com/developers/documentation/http/documentation#files-delete
     *
     * @param id File name.
     */
    public Result<Void> delete(String id) {
        // TODO: TO BE COMPLETED
        try {
            OAuthRequest deleteFolder = new OAuthRequest(Verb.POST, DELETE_FILE_V2_URL);
            deleteFolder.addHeader("Content-Type", JSON_CONTENT_TYPE);

            deleteFolder.setPayload(JSON.encode(new DeleteFileV2Args("/mediastorage/" + id + ".jpg")));

            service.signRequest(accessToken, deleteFolder);
            Response r = service.execute(deleteFolder);

            System.err.println("BODY : " + r.getBody());
            System.err.println("CODE : " + r.getCode());
            System.err.println("MESSAGE : " + r.getMessage());


            if (r.getCode() == 409) {
                System.err.println("File does not exist");
                return Result.error(Result.ErrorCode.NOT_FOUND);
            } else if (r.getCode() == 200) {
                System.err.println("File deleted with success");
                return Result.ok();
            } else {
                System.err.println("Unexpected error HTTP: " + r.getCode());
                return Result.error(Result.ErrorCode.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

}