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
package org.sonar.db.issue;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.core.issue.DefaultIssueComment;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.stream.Collectors;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.MyBatis;

import static java.util.Collections.singletonList;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;

public class IssueChangeDao implements Dao {

  private final MyBatis mybatis;

  public IssueChangeDao(MyBatis mybatis) {
    this.mybatis = mybatis;
  }

  public List<DefaultIssueComment> selectCommentsByIssues(DbSession session, Collection<String> issueKeys) {
    List<DefaultIssueComment> comments = Lists.newArrayList();
    for (IssueChangeDto dto : selectByTypeAndIssueKeys(session, issueKeys, IssueChangeDto.TYPE_COMMENT)) {
      comments.add(dto.toComment());
    }
    return comments;
  }

  public List<FieldDiffs> selectChangelogByIssue(DbSession session, String issueKey) {
    return selectByTypeAndIssueKeys(session, singletonList(issueKey), IssueChangeDto.TYPE_FIELD_CHANGE)
      .stream()
      .map(IssueChangeDto::toFieldDiffs)
      .collect(Collectors.toList());
  }

  public List<IssueChangeDto> selectChangelogOfNonClosedIssuesByComponent(String componentUuid) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = mapper(session);
      return mapper.selectChangelogOfNonClosedIssuesByComponent(componentUuid, IssueChangeDto.TYPE_FIELD_CHANGE);

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  @CheckForNull
  public DefaultIssueComment selectCommentByKey(String commentKey) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = mapper(session);
      IssueChangeDto dto = mapper.selectByKeyAndType(commentKey, IssueChangeDto.TYPE_COMMENT);
      return dto != null ? dto.toComment() : null;

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public List<IssueChangeDto> selectByTypeAndIssueKeys(DbSession session, Collection<String> issueKeys, String changeType) {
    return executeLargeInputs(issueKeys, issueKeys1 -> mapper(session).selectByIssuesAndType(issueKeys1, changeType));
  }

  public void insert(DbSession session, IssueChangeDto change) {
    mapper(session).insert(change);
  }

  public boolean delete(String key) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = mapper(session);
      int count = mapper.delete(key);
      session.commit();
      return count == 1;

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public boolean update(IssueChangeDto change) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = mapper(session);
      int count = mapper.update(change);
      session.commit();
      return count == 1;

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  private static IssueChangeMapper mapper(DbSession session) {
    return session.getMapper(IssueChangeMapper.class);
  }
}
