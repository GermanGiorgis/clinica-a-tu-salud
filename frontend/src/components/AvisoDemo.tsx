import { useEffect, useState } from "react";
import { Info } from "@phosphor-icons/react";
import { suscribirseALentitud } from "../api/client";
import { modoDemo } from "../config/demo";
import "./AvisoDemo.css";

/**
 * Franja fina arriba de todo en la versión de demostración: deja claro que los datos son de prueba y, cuando un pedido
 * tarda, explica por qué (el servidor gratuito se duerme sin visitas y la primera vez tarda en despertar).
 */
export function AvisoDemo() {
  const [despertando, setDespertando] = useState(false);

  useEffect(() => suscribirseALentitud(setDespertando), []);

  if (!modoDemo()) {
    return null;
  }
  return (
    <div className="aviso-demo" role="note">
      <Info size={16} weight="fill" aria-hidden="true" />
      <span>
        Versión de demostración: los datos se borran cada tanto. <strong>No ingreses datos personales reales.</strong>
        {despertando && (
          <>
            {" "}El servidor de la demo estaba dormido y se está despertando: la primera vez puede tardar hasta un minuto.
          </>
        )}
      </span>
    </div>
  );
}
