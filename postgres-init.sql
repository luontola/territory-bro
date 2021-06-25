create extension if not exists postgis;
create extension if not exists pgcrypto;
-- basic hardening due to GIS users
revoke create on schema public from public;
