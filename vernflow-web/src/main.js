import { createApp } from "vue";
import VueFinder from "vuefinder";
import App from "./App.vue";
import router from "./router";
import "vuefinder/dist/style.css";
import "./styles.css";
import "./styles/tourist.css";
import "./styles/tourism-prototype.css";

createApp(App).use(router).use(VueFinder).mount("#app");
