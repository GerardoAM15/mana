-- Actualiza contratos de v3 a v4 sin borrar registros.
-- Ejecutar como MANADB. En DBeaver: ejecutar el script completo (Alt + X).
-- Puede repetirse: omite los objetos que ya existen.
-- Oracle confirma automáticamente los cambios DDL.
declare
    v_restriccion number;
    v_indice number;
    v_duplicados number;
begin
    select count(*) into v_duplicados
      from (select anterior_id from contratos
             where estado <> 'ANULADO' and anterior_id is not null
             group by anterior_id having count(*) > 1);
    if v_duplicados > 0 then
        raise_application_error(-20001, 'Hay renovaciones vigentes duplicadas; revisar antes de migrar');
    end if;

    select count(*) into v_restriccion from user_constraints
     where table_name = 'CONTRATOS'
       and constraint_name = 'UQ_CONTRATOS_ANTERIOR' and constraint_type = 'U';
    if v_restriccion > 0 then
        -- Crear primero la protección nueva, antes de retirar la antigua.
        select count(*) into v_indice from user_indexes
         where index_name = 'UQ_CONTRATOS_ANTERIOR_V4';
        if v_indice = 0 then
            execute immediate 'create unique index uq_contratos_anterior_v4 on contratos
                (case when estado <> ''ANULADO'' then anterior_id end)';
        end if;
        execute immediate 'alter table contratos drop constraint uq_contratos_anterior';
        execute immediate 'alter index uq_contratos_anterior_v4 rename to uq_contratos_anterior';
    else
        select count(*) into v_indice from user_indexes
         where index_name = 'UQ_CONTRATOS_ANTERIOR';
        if v_indice = 0 then
            select count(*) into v_indice from user_indexes
             where index_name = 'UQ_CONTRATOS_ANTERIOR_V4';
            if v_indice > 0 then
                execute immediate 'alter index uq_contratos_anterior_v4 rename to uq_contratos_anterior';
            else
                execute immediate 'create unique index uq_contratos_anterior on contratos
                    (case when estado <> ''ANULADO'' then anterior_id end)';
            end if;
        end if;
    end if;

    -- Conserva búsquedas por la FK incluso para renovaciones anuladas.
    select count(*) into v_indice from user_indexes
     where index_name = 'IX_CONTRATOS_ANTERIOR';
    if v_indice = 0 then
        execute immediate 'create index ix_contratos_anterior on contratos (anterior_id)';
    end if;
end;
/
