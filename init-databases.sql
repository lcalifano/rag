CREATE DATABASE users_db;
CREATE DATABASE chat_db;
CREATE DATABASE document_db;

\c document_db;
CREATE EXTENSION IF NOT EXISTS vector;

-- La colonna embedding verrà aggiunta da Hibernate ddl-auto,
-- ma il cast a vector richiede l'estensione attiva prima.
