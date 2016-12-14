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
// @flow
import { getJSON } from '../helpers/request';

type GetProjectActivityResponse = {
  analyses: Array<Object>,
  paging: {
    total: number,
    pageIndex: number,
    pageSize: number
  }
};

type Options = {
  category?: ?string,
  pageIndex?: ?number,
  pageSize?: ?number
};

export const getProjectActivity = (
    project: string,
    options?: Options
): Promise<GetProjectActivityResponse> => {
  const data: Object = { project };
  if (options) {
    if (options.category) {
      data.category = options.category;
    }
    if (options.pageIndex) {
      data.p = options.pageIndex;
    }
    if (options.pageSize) {
      data.ps = options.pageSize;
    }
  }

  return getJSON('/api/project_analyses/search', data);
};
