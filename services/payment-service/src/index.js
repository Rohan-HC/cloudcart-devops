const { createApp } = require("./server");

const port = Number(process.env.PORT || 8084);
const app = createApp();

app.listen(port, () => {
  console.log(`payment-service listening on port ${port}`);
});
