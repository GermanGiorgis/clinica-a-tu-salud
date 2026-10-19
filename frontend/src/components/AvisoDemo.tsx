import { Info } from "@phosphor-icons/react";
import { modoDemo } from "../config/demo";
import "./AvisoDemo.css";

/** Franja fina arriba de todo en la versión de demostración: deja claro que los datos son de prueba. */
export function AvisoDemo() {
  if (!modoDemo()) {
    return null;
  }
  return (
    <div className="aviso-demo" role="note">
      <Info size={16} weight="fill" aria-hidden="true" />
      <span>
        Versión de demostración: los datos se borran cada tanto. <strong>No ingreses datos personales reales.</strong>
      </span>
    </div>
  );
}
