import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Política de Privacidad — PR4Y',
  description: 'Política de Privacidad del servicio PR4Y. Requerida para Google Play Store.',
};

export default function PrivacyPage() {
  return (
    <main className="min-h-screen bg-background px-6 py-12">
      <article className="mx-auto max-w-3xl prose prose-slate dark:prose-invert">
        <h1>Política de Privacidad</h1>
        <p className="lead">
          Última actualización: febrero de 2025. Esta política aplica al sitio web pr4y.cl y a la
          aplicación móvil PR4Y (Android).
        </p>

        <h2>1. Responsable del tratamiento</h2>
        <p>
          Los datos personales que puedas facilitar al usar PR4Y son tratados bajo la responsabilidad
          del proyecto PR4Y (&quot;nosotros&quot;, &quot;el servicio&quot;), accesible a través del
          sitio web <strong>pr4y.cl</strong> y de la aplicación para Android &quot;PR4Y&quot;.
        </p>

        <h2>2. Datos que recogemos</h2>
        <ul>
          <li>
            <strong>Cuenta:</strong> dirección de correo electrónico y contraseña (almacenada de
            forma segura, hasheada). Solo usamos el email para identificarte y permitir el acceso al
            servicio.
          </li>
          <li>
            <strong>Contenido de oraciones y diario:</strong> se cifra en tu dispositivo (E2EE) antes
            de enviarse a nuestros servidores. Nosotros no tenemos la clave para descifrarlo; no
            podemos leer ni acceder al contenido de tus oraciones.
          </li>
          <li>
            <strong>Metadatos de uso:</strong> para el correcto funcionamiento del servicio
            (sincronización) podemos registrar datos técnicos no sensibles, como la fecha de
            última sincronización o el volumen de datos sincronizados, sin acceso al contenido.
          </li>
        </ul>

        <h2>3. Finalidad y base legal</h2>
        <p>
          Utilizamos tus datos para proporcionar el servicio (cuenta, sincronización segura y
          cifrada), mejorar la estabilidad y el rendimiento, y cumplir obligaciones legales cuando
          corresponda. La base legal es la ejecución del contrato y, cuando aplique, tu consentimiento.
        </p>

        <h2>4. Cifrado y seguridad (Zero-Knowledge)</h2>
        <p>
          PR4Y utiliza cifrado de extremo a extremo (E2EE) de tipo Zero-Knowledge para el contenido
          que escribes (oraciones, entradas de diario). Eso significa que el contenido se cifra en
          tu dispositivo y solo tú posees la clave para descifrarlo. Nosotros no podemos leer ni
          descifrar ese contenido en ningún momento.
        </p>

        <h2>5. Conservación y eliminación</h2>
        <p>
          Conservamos los datos de tu cuenta y los metadatos necesarios mientras mantengas la cuenta
          activa. Puedes solicitar la eliminación de tu cuenta y de los datos asociados; en ese caso
          procederemos a borrarlos en un plazo razonable, salvo retención legal.
        </p>

        <h2>6. Derechos</h2>
        <p>
          Puedes ejercer los derechos de acceso, rectificación, supresión, limitación del tratamiento,
          portabilidad y oposición respecto de los datos que tratamos, dirigiendo una solicitud a
          la dirección de contacto que indiquemos en la aplicación o en este sitio. También tienes
          derecho a reclamar ante la autoridad de protección de datos competente.
        </p>

        <h2>7. Cambios</h2>
        <p>
          Podemos actualizar esta política de privacidad. La versión vigente estará publicada en esta
          página con la fecha de última actualización. El uso continuado del servicio tras cambios
          relevantes puede entenderse como aceptación de la nueva política.
        </p>

        <p>
          Para dudas sobre esta política o sobre el tratamiento de tus datos, puedes contactarnos
          a través de los medios indicados en la aplicación o en pr4y.cl.
        </p>
      </article>
    </main>
  );
}
