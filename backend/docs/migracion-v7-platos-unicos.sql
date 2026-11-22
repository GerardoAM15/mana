-- MANÁ v7: unificar platos equivalentes y proteger nombre + categoría.
-- Ejecutar como MANADB, con la aplicación detenida y un respaldo previo de:
-- platos, menu_platos, detalles_consumo, detalles_orden y detalle_combo_platos.
-- Conserva el menor ID, activa el resultado si algún duplicado estaba activo,
-- redirige referencias y suma cantidades cuando coinciden dos detalles.
-- No cambia precios, importes, descripciones ni categorías históricas.
-- Oracle confirma DML antes de crear el índice (DDL).
declare
    v_indice number;
    v_conflictos number;
    v_total_consumo number;
    v_total_combo number;
    v_despues number;
begin
    select count(*) into v_indice from user_indexes where index_name = 'UQ_PLATOS_NOMBRE_CATEGORIA';
    if v_indice > 0 then return; end if;

    -- No unificar si existen referencias de módulos que esta migración desconoce.
    select count(*) into v_conflictos from user_constraints
     where constraint_type = 'R'
       and r_constraint_name in (select constraint_name from user_constraints where table_name='PLATOS' and constraint_type in ('P','U'))
       and table_name not in ('MENU_PLATOS','DETALLES_CONSUMO','DETALLES_ORDEN','DETALLE_COMBO_PLATOS');
    if v_conflictos > 0 then raise_application_error(-20007, 'Hay referencias nuevas a platos: revisar antes de unificar'); end if;

    lock table platos in exclusive mode;
    lock table menu_platos in exclusive mode;
    lock table detalles_consumo in exclusive mode;
    lock table detalles_orden in exclusive mode;
    lock table detalle_combo_platos in exclusive mode;
    select count(*) into v_conflictos from platos where trim(regexp_replace(nombre, '[[:space:]]+', ' ')) is null;
    if v_conflictos > 0 then raise_application_error(-20007, 'Existen platos sin nombre válido: corregirlos antes de migrar'); end if;
    select nvl(sum(cantidad),0) into v_total_consumo from detalles_consumo;
    select nvl(sum(cantidad),0) into v_total_combo from detalle_combo_platos;

    for d in (
        select id, destino, activo_final from (
            select id, min(id) over (partition by categoria, upper(trim(regexp_replace(nombre, '[[:space:]]+', ' ')))) destino,
                   max(activo) over (partition by categoria, upper(trim(regexp_replace(nombre, '[[:space:]]+', ' ')))) activo_final
              from platos
        ) where id <> destino order by destino, id
    ) loop
        select count(*) into v_conflictos from detalles_consumo a join detalles_consumo b
          on a.consumo_id=b.consumo_id and a.categoria_consumida<>b.categoria_consumida
         where a.plato_id=d.id and b.plato_id=d.destino;
        if v_conflictos > 0 then raise_application_error(-20007, 'Un duplicado tiene categorías históricas incompatibles: revisar manualmente'); end if;

        delete from menu_platos m where m.plato_id=d.id
          and exists (select 1 from menu_platos c where c.menu_id=m.menu_id and c.plato_id=d.destino);
        update menu_platos set plato_id=d.destino where plato_id=d.id;

        update detalles_consumo c set cantidad=c.cantidad +
            (select a.cantidad from detalles_consumo a where a.consumo_id=c.consumo_id and a.plato_id=d.id)
         where c.plato_id=d.destino and exists (select 1 from detalles_consumo a where a.consumo_id=c.consumo_id and a.plato_id=d.id);
        delete from detalles_consumo a where a.plato_id=d.id
          and exists (select 1 from detalles_consumo c where c.consumo_id=a.consumo_id and c.plato_id=d.destino);
        update detalles_consumo set plato_id=d.destino where plato_id=d.id;

        update detalle_combo_platos c set cantidad=c.cantidad +
            (select a.cantidad from detalle_combo_platos a where a.detalle_id=c.detalle_id and a.plato_id=d.id)
         where c.plato_id=d.destino and exists (select 1 from detalle_combo_platos a where a.detalle_id=c.detalle_id and a.plato_id=d.id);
        delete from detalle_combo_platos a where a.plato_id=d.id
          and exists (select 1 from detalle_combo_platos c where c.detalle_id=a.detalle_id and c.plato_id=d.destino);
        update detalle_combo_platos set plato_id=d.destino where plato_id=d.id;
        update detalles_orden set plato_id=d.destino where plato_id=d.id;
        update platos set activo=d.activo_final where id=d.destino;
        delete from platos where id=d.id;
    end loop;
    update platos set nombre=trim(regexp_replace(nombre, '[[:space:]]+', ' '))
     where nombre<>trim(regexp_replace(nombre, '[[:space:]]+', ' '));
    select nvl(sum(cantidad),0) into v_despues from detalles_consumo;
    if v_despues<>v_total_consumo then raise_application_error(-20007, 'Cambió el total consumido: revertir'); end if;
    select nvl(sum(cantidad),0) into v_despues from detalle_combo_platos;
    if v_despues<>v_total_combo then raise_application_error(-20007, 'Cambió el total de platos de combos: revertir'); end if;

    -- REGEXP_REPLACE no puede usarse en un índice funcional de Oracle.
    -- El trigger normaliza incluso escrituras directas; UPPER/TRIM sí son indexables.
    execute immediate 'create or replace trigger tr_platos_normalizar_nombre
        before insert or update of nombre on platos for each row
        begin :new.nombre := trim(regexp_replace(:new.nombre, ''[[:space:]]+'', '' '')); end;';
    execute immediate 'create unique index uq_platos_nombre_categoria on platos
        (categoria, upper(trim(nombre)))';
exception
    when others then rollback; raise;
end;
/
