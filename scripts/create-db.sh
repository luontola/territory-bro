#!/bin/bash
set -eu

echo -n "Database hostname: "
read dbhost

echo -n "New account username: "
read username
if [[ ! $username =~ ^[a-z0-9]+$ ]]; then
    echo "ERROR: Username must be lowercase alphanumeric"
    exit 1
fi

echo -n "New account password: "
read -s password
echo

echo "
CREATE USER $username WITH PASSWORD '$password';

-- because of RDS, see http://stackoverflow.com/a/34898033/62130
GRANT $username TO master;

CREATE DATABASE $username
    OWNER $username
    ENCODING 'UTF-8'
    LC_COLLATE 'fi_FI.UTF-8'
    LC_CTYPE 'fi_FI.UTF-8'
    TEMPLATE template0;

\connect $username;

CREATE SCHEMA $username AUTHORIZATION $username;

CREATE EXTENSION postgis;
" | psql -h $dbhost -U master territorybro
