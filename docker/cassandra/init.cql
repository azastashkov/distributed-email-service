CREATE KEYSPACE IF NOT EXISTS email_service
  WITH replication = {'class':'NetworkTopologyStrategy','dc1':3};

USE email_service;

CREATE TABLE IF NOT EXISTS users_by_email (
  email         text PRIMARY KEY,
  user_id       uuid,
  password_hash text,
  display_name  text,
  created_at    timestamp
);

CREATE TABLE IF NOT EXISTS users_by_id (
  user_id      uuid PRIMARY KEY,
  email        text,
  display_name text,
  created_at   timestamp
);

CREATE TABLE IF NOT EXISTS folders_by_user (
  user_id       uuid,
  folder_id     uuid,
  name          text,
  system_folder boolean,
  created_at    timestamp,
  PRIMARY KEY ((user_id), folder_id)
);

CREATE TABLE IF NOT EXISTS emails_by_folder (
  user_id         uuid,
  folder_id       uuid,
  received_at     timestamp,
  email_id        timeuuid,
  subject         text,
  from_addr       text,
  to_addrs        set<text>,
  preview         text,
  has_attachments boolean,
  is_read         boolean,
  PRIMARY KEY ((user_id, folder_id), received_at, email_id)
) WITH CLUSTERING ORDER BY (received_at DESC, email_id DESC);

CREATE TABLE IF NOT EXISTS emails_by_id (
  user_id         uuid,
  email_id        timeuuid,
  folder_id       uuid,
  subject         text,
  body            text,
  from_addr       text,
  to_addrs        set<text>,
  cc_addrs        set<text>,
  bcc_addrs       set<text>,
  received_at     timestamp,
  is_read         boolean,
  attachment_keys list<text>,
  PRIMARY KEY ((user_id), email_id)
);

CREATE TABLE IF NOT EXISTS emails_by_read_status (
  user_id        uuid,
  is_read        boolean,
  received_month int,
  received_at    timestamp,
  email_id       timeuuid,
  folder_id      uuid,
  subject        text,
  from_addr      text,
  preview        text,
  PRIMARY KEY ((user_id, is_read, received_month), received_at, email_id)
) WITH CLUSTERING ORDER BY (received_at DESC, email_id DESC);
