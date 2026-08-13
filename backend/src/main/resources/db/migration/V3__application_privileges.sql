-- The application role is supplied as a Flyway placeholder. The role is created by
-- the database platform, not by application migrations, so its password never enters
-- source control or Flyway history.
DO $$
DECLARE
    application_role TEXT := '${applicationRole}';
    table_name TEXT;
    sequence_name TEXT;
    function_signature TEXT;
BEGIN
    IF application_role !~ '^[a-z_][a-z0-9_]{0,62}$' THEN
        RAISE EXCEPTION 'Application database role must be a simple PostgreSQL identifier';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = application_role) THEN
        RAISE EXCEPTION 'Application database role % does not exist', application_role;
    END IF;

    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', application_role);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', application_role);

    FOR table_name IN
        SELECT quote_ident(tablename)
        FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%s TO %I', table_name, application_role);
    END LOOP;

    FOR table_name IN
        SELECT quote_ident(viewname)
        FROM pg_views
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('GRANT SELECT ON TABLE public.%s TO %I', table_name, application_role);
    END LOOP;

    FOR sequence_name IN
        SELECT quote_ident(sequencename)
        FROM pg_sequences
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE public.%s TO %I', sequence_name, application_role);
    END LOOP;

    FOR function_signature IN
        SELECT p.oid::regprocedure::TEXT
        FROM pg_proc AS p
        JOIN pg_namespace AS n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
    LOOP
        EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO %I', function_signature, application_role);
    END LOOP;

    EXECUTE format('REVOKE ALL ON TABLE public.flyway_schema_history FROM %I', application_role);
    EXECUTE format('REVOKE UPDATE, DELETE ON TABLE public.fuel_inventory_transactions FROM %I', application_role);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
        application_role
    );
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I',
        application_role
    );
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO %I',
        application_role
    );
END;
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
