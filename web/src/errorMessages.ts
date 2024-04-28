// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import i18n from "./i18n";

function formatErrorMessage(error) {
  const key = error[0];
  if (key === 'no-such-user') {
    const userId = error[1];
    return `${i18n.t('UserManagement.userIdNotExist')}: ${userId}`;
  }
  return JSON.stringify(error);
}

export function formatApiError(error) {
  const errors = error.response?.data.errors;
  if (Array.isArray(errors)) {
    const messages = errors.map(formatErrorMessage);
    return messages.join("\n");
  }
  return error;
}
