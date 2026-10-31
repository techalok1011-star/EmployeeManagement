import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";

// No <StrictMode>: its development double-mount would create (and destroy) the WebGL map twice.
createRoot(document.getElementById("root")!).render(<App />);
