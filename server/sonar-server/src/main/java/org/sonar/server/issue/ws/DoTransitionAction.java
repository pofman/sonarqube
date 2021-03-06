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
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.issue.IssueUpdater;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.user.UserSession;

public class DoTransitionAction implements IssuesWsAction {

  public static final String ACTION = "do_transition";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final IssueFinder issueFinder;
  private final IssueUpdater issueUpdater;
  private final TransitionService transitionService;
  private final OperationResponseWriter responseWriter;

  public DoTransitionAction(DbClient dbClient, UserSession userSession, IssueFinder issueFinder, IssueUpdater issueUpdater, TransitionService transitionService,
    OperationResponseWriter responseWriter) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.issueFinder = issueFinder;
    this.issueUpdater = issueUpdater;
    this.transitionService = transitionService;
    this.responseWriter = responseWriter;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION)
      .setDescription("Do workflow transition on an issue. Requires authentication and Browse permission on project.<br/>" +
        "The transitions '" + DefaultTransitions.WONT_FIX + "' and '" + DefaultTransitions.FALSE_POSITIVE + "' require the permission 'Administer Issues'.")
      .setSince("3.6")
      .setHandler(this)
      .setPost(true);

    action.createParam("issue")
      .setDescription("Key of the issue")
      .setRequired(true)
      .setExampleValue("5bccd6e8-f525-43a2-8d76-fcb13dde79ef");
    action.createParam("transition")
      .setDescription("Transition")
      .setRequired(true)
      .setPossibleValues(DefaultTransitions.ALL);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn();
    String issue = request.mandatoryParam("issue");
    try (DbSession dbSession = dbClient.openSession(false)) {
      IssueDto issueDto = issueFinder.getByKey(dbSession, issue);
      doTransition(dbSession, issueDto.toDefaultIssue(), request.mandatoryParam("transition"));
      responseWriter.write(issue, request, response);
    }
  }

  private void doTransition(DbSession session, DefaultIssue defaultIssue, String transitionKey) {
    IssueChangeContext context = IssueChangeContext.createUser(new Date(), userSession.getLogin());
    transitionService.checkTransitionPermission(transitionKey, defaultIssue);
    if (transitionService.doTransition(defaultIssue, context, transitionKey)) {
      issueUpdater.saveIssue(session, defaultIssue, context, null);
    }
  }
}
