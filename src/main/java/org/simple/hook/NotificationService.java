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
import com.sendgrid.Method;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.IOException;

/**
 * Git hook listener class.
 */
@Path("/listener")
public class NotificationService {

    private final OkHttpClient httpClient;
    private final String ISSUE_REPORTER_KEY = "issue.user.login";
    private final String ISSUE_NUMBER_KEY = "issue.number";
    private final String ISSUE_URL_KEY = "issue.html_url";
    private final String ISSUE_TITLE_KEY = "issue.title";
    private final String ACTION_KEY = "action";

    public NotificationService() {

        httpClient = new OkHttpClient().newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    @POST
    @Path("/notify")
    @Consumes("application/json")
    public void post(JsonObject data, @QueryParam("githubToken") String githubToken,
                     @QueryParam("sendgridToken") String sendgridToken, @QueryParam("sender") String sender,
                     @QueryParam("to") String to) {

        if (!"opened".equals(getValue(ACTION_KEY, data))
                || isMember(getValue(ISSUE_REPORTER_KEY, data), githubToken)) {
            return;
        }

        sendEmail(data, sendgridToken, sender, to);
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

    private boolean isMember(String user, String token) {

        // Token = base64encoded(<github_username>:<github_personal_token>)
        Request request = new Request.Builder()
                .url("https://api.github.com/orgs/wso2/members/" + user)
                .addHeader("Authorization", "Basic " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.code() == 204;
        } catch (IOException e) {
            // Too lazy to properly handle exception.
            e.printStackTrace();
            return true;
        }
    }

    private void sendEmail(JsonObject data, String sendgridToken, String sender, String to) {

        Email from = new Email(sender);
        Personalization personalization = new Personalization();
        String[] tos = to.replaceAll(" ", "").split(",");

        for (String rec : tos) {
            personalization.addTo(new Email(rec));
        }

        Mail mail = new Mail();
        mail.addPersonalization(personalization);
        mail.setFrom(from);
        mail.setSubject(getSubject(data));
        mail.addContent(new Content("text/html", getContent(data)));

        SendGrid sg = new SendGrid(sendgridToken);
        com.sendgrid.Request request = new com.sendgrid.Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            com.sendgrid.Response response = sg.api(request);
            System.out.println(response.getStatusCode());
            System.out.println(response.getBody());
            System.out.println(response.getHeaders());
        } catch (IOException e) {
            // Too lazy to properly handle exception.
            e.printStackTrace();
        }
    }

    private String getContent(JsonObject data) {

        return String.format(
                "<html>" +
                "<body>" +
                "  <div><code>Issue: %s</code></div>" +
                "  <div><code>Reporter: %s</code></div>" +
                "  <div><code>Link: %s</code></div>" +
                "</body>" +
                "</html>",
                getValue(ISSUE_TITLE_KEY, data),
                getValue(ISSUE_REPORTER_KEY, data),
                getValue(ISSUE_URL_KEY, data));
    }

    private String getSubject(JsonObject data) {

        return String.format("[Git-Issue] New git issue #%s from non member", getValue(ISSUE_NUMBER_KEY, data));
    }
}
