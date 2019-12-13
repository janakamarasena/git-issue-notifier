/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.simple.hook;

import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Git hook listener class.
 */
@Path("/listener")
public class NotificationService {

    private final String ISSUE_REPORTER_KEY = "issue.user.login";
    private final String ISSUE_NUMBER_KEY = "issue.number";
    private final String REPO_NAME_KEY = "repository.name";
    private final String REPO_FULL_NAME_KEY = "repository.full_name";
    private final String ISSUE_URL_KEY = "issue.html_url";
    private final String ISSUE_TITLE_KEY = "issue.title";
    private final String ACTION_KEY = "action";
    private final String GITHUB_TOKEN;
    private final String CONFIG_FILE_PATH;
    private final String EXCLUDE_LIST_KEY= "excludeList";
    private volatile List<String> excludeList;
    private OkHttpClient httpClient;
    private Mailer mailer;

    public NotificationService() {

        GITHUB_TOKEN = System.getenv("GIT_ISSUE_HOOK_GITHUB_TOKEN");

        httpClient = new OkHttpClient().newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        mailer = MailerBuilder
                .withSMTPServer(
                        System.getenv("GIT_ISSUE_HOOK_EMAIL_HOST"),
                        Integer.valueOf(System.getenv("GIT_ISSUE_HOOK_EMAIL_PORT")),
                        System.getenv("GIT_ISSUE_HOOK_EMAIL_USERNAME"),
                        System.getenv("GIT_ISSUE_HOOK_EMAIL_PASSWORD"))
                .buildMailer();

        CONFIG_FILE_PATH =
                new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                        "/config.properties";

        loadExcludeList();
    }

    @POST
    @Path("/notify")
    @Consumes(MediaType.APPLICATION_JSON)
    public void post(JsonObject data, @QueryParam("to") String to) {

        if (StringUtils.isBlank(to)) {
            return;
        }

        if (!"opened".equals(getValue(ACTION_KEY, data))) {
            return;
        }

        String issueReporter = getValue(ISSUE_REPORTER_KEY, data);
        if (excludeList.contains(issueReporter) || isMember(issueReporter)) {
            return;
        }

        sendEmail(data, to);
    }

    @POST
    @Path("/excludes")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addToExcludeList(@FormParam("exclude") String values) {

        List<String> tempExcludeList = new ArrayList<>(Arrays.asList(values.replaceAll(" ", "").split(",")));
        for (String exclude : tempExcludeList) {
            if (!excludeList.contains(exclude)) {
                excludeList.add(exclude);
            }
        }
        saveExcludeList();

        return Response.ok().entity(excludeList).build();
    }

    @GET
    @Path("/excludes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExcludeList() {

        return Response.ok().entity(excludeList).build();
    }

    @DELETE
    @Path("/excludes")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void deleteFromExcludeList(@FormParam("exclude") String values) {

        List<String> tempExcludeList = new ArrayList<>(Arrays.asList(values.replaceAll(" ", "").split(",")));
        excludeList.removeAll(tempExcludeList);
        saveExcludeList();
    }

    private String getValue(String key, JsonObject data) {

        try {
            String[] keys = key.split("\\.");
            JsonObject elm = data;
            for (int i = 0; i < keys.length - 1; i++) {
                elm = elm.get(keys[i]).getAsJsonObject();
            }
            return elm.get(keys[keys.length - 1]).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isMember(String user) {

        Request request = new Request.Builder()
                .url("https://api.github.com/orgs/wso2/members/" + user)
                .addHeader("Authorization", "Basic " + GITHUB_TOKEN)
                .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 204) {
                return true;
            }
        } catch (IOException e) {
            // Too lazy to properly handle exception.
            e.printStackTrace();
            return true;
        }

        request = new Request.Builder()
                .url("https://api.github.com/orgs/wso2-support/members/" + user)
                .addHeader("Authorization", "Basic " + GITHUB_TOKEN)
                .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            return response.code() == 204;
        } catch (IOException e) {
            // Too lazy to properly handle exception.
            e.printStackTrace();
            return true;
        }
    }

    private void sendEmail(JsonObject data, String to) {

        Email email = EmailBuilder.startingBlank()
                .from("git-issue-hook", "gitissuehook@wso2.com")
                .toMultiple(to)
                .withSubject(getSubject(data))
                .withHTMLText(getContent(data))
                .buildEmail();

        mailer.sendMail(email);
    }

    private String getContent(JsonObject data) {

        return String.format(
                "<html>" +
                "<body>" +
                "  <div><code>Repo: %s</code></div>" +
                "  <div><code>Issue: %s</code></div>" +
                "  <div><code>Reporter: %s</code></div>" +
                "  <div><code>Link: %s</code></div>" +
                "</body>" +
                "</html>",
                getValue(REPO_FULL_NAME_KEY, data),
                getValue(ISSUE_TITLE_KEY, data),
                getValue(ISSUE_REPORTER_KEY, data),
                getValue(ISSUE_URL_KEY, data));
    }

    private String getSubject(JsonObject data) {

        return String.format("[Git-Issue] New git issue %s#%s from non member", getValue(REPO_NAME_KEY, data),
                getValue(ISSUE_NUMBER_KEY, data));
    }

    private void loadExcludeList() {

        File file = new File(CONFIG_FILE_PATH);

        if (!file.exists()){
            excludeList = new ArrayList<>();
            return;
        }

        try (InputStream input = new FileInputStream(file)) {

            Properties prop = new Properties();
            prop.load(input);
            String excludes = prop.getProperty(EXCLUDE_LIST_KEY);

            if (StringUtils.isBlank(excludes)) {
                excludeList = new ArrayList<>();
                return;
            }

            excludeList = new ArrayList<>(Arrays.asList(excludes.split(",")));

        } catch (IOException e) {
            // Too lazy to properly handle exception.
            excludeList = new ArrayList<>();
            e.printStackTrace();
        }
    }

    private void saveExcludeList() {

        try (OutputStream output = new FileOutputStream(CONFIG_FILE_PATH)) {
            Properties prop = new Properties();
            prop.setProperty(EXCLUDE_LIST_KEY, StringUtils.join(excludeList, ","));
            prop.store(output, null);
        } catch (IOException e) {
            // Too lazy to properly handle exception.
            e.printStackTrace();
        }
    }
}
