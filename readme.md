A simple git hook listener to listen for git issues and notify to emails if the issue is from a non member.

### Build 
```
mvn package
```

### Run
```
java -jar target/Notification-Service-1.0.0.jar
```

### Environment Variables

```
GIT_ISSUE_HOOK_EMAIL_HOST=<email server host>
GIT_ISSUE_HOOK_EMAIL_PORT=<email server port>
GIT_ISSUE_HOOK_EMAIL_USERNAME=<email username>
GIT_ISSUE_HOOK_EMAIL_PASSWORD=<email password>
GIT_ISSUE_HOOK_PORT=<port to start the service>
GIT_ISSUE_HOOK_GITHUB_TOKEN=<base64encoded(<github_username>:<github_personal_token>)>
```
Having just the `read:org` scope is sufficient for the github_personal_token[1]

[1] - https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token-for-the-command-line

### Sample calls
```
You can add a git hook for issues in the following way
[POST] /listener/notify?to=joe@sample.com,jane@sample.com

Endpoint to just check the server availability
[GET] /status
```