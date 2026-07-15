const { createApp } = require("./server");

const port = Number(process.env.PORT || 8085);
const app = createApp();

app.listen(port, () => {
  console.log(`notification-service listening on port ${port}`);
});
