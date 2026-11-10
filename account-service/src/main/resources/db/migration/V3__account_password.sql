-- Credencial de login: o documento (CPF) identifica o cliente e a senha fica guardada só como hash BCrypt.
ALTER TABLE account ADD COLUMN password_hash VARCHAR(100);

-- Contas pré-carregadas: senha de demonstração "senha123" (hash BCrypt, custo 10)
UPDATE account SET password_hash = '$2a$10$5kuQyc6OrwUsEfo4P/sxweaPe07MPxCk3TJfEZfr4INKCNTzrtWwS';

ALTER TABLE account ALTER COLUMN password_hash SET NOT NULL;
