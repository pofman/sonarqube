/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.issue.ws;

import java.util.Date;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueFieldsSetter;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.issue.IssueUpdater;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.client.issue.IssuesWsParameters;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.sonar.core.util.Uuids.UUID_EXAMPLE_01;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_ISSUE;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_TEXT;

public class AddCommentAction implements IssuesWsAction {

  private final System2 system2;
  private final UserSession userSession;
  private final DbClient dbClient;
  private final IssueFinder issueFinder;
  private final IssueUpdater issueUpdater;
  private final IssueFieldsSetter issueFieldsSetter;
  private final OperationResponseWriter responseWriter;

  public AddCommentAction(System2 system2, UserSession userSession, DbClient dbClient, IssueFinder issueFinder, IssueUpdater issueUpdater, IssueFieldsSetter issueFieldsSetter,
    OperationResponseWriter responseWriter) {
    this.system2 = system2;
    this.userSession = userSession;
    this.dbClient = dbClient;
    this.issueFinder = issueFinder;
    this.issueUpdater = issueUpdater;
    this.issueFieldsSetter = issueFieldsSetter;
    this.responseWriter = responseWriter;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(IssuesWsParameters.ACTION_ADD_COMMENT)
      .setDescription("Add a comment.<br/>" +
        "Requires authentication and Browse permission on project of the specified issue")
      .setSince("3.6")
      .setHandler(this)
      .setPost(true);

    action.createParam(PARAM_ISSUE)
      .setDescription("Issue key")
      .setRequired(true)
      .setExampleValue(UUID_EXAMPLE_01);
    action.createParam(PARAM_TEXT)
      .setDescription("Comment text")
      .setRequired(true)
      .setExampleValue("blabla...");
  }

  @Override
  public void handle(Request request, Response response) {
    userSession.checkLoggedIn();
    String issueKey = request.mandatoryParam(PARAM_ISSUE);
    String commentText = request.mandatoryParam(PARAM_TEXT);
    checkArgument(!isNullOrEmpty(commentText), "Cannot add empty comment to an issue");
    try (DbSession dbSession = dbClient.openSession(false)) {
      IssueDto issueDto = issueFinder.getByKey(dbSession, issueKey);
      IssueChangeContext context = IssueChangeContext.createUser(new Date(system2.now()), userSession.getLogin());
      DefaultIssue defaultIssue = issueDto.toDefaultIssue();
      issueFieldsSetter.addComment(defaultIssue, commentText, context);
      issueUpdater.saveIssue(dbSession, defaultIssue, context, commentText);
      responseWriter.write(issueKey, request, response);
    }
  }
}
