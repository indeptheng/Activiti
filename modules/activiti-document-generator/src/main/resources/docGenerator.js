var   fs            = require('fs');
var   Docxtemplater = require('docxtemplater');
const readline      = require('readline');

const rl = readline.createInterface({
  input:    process.stdin,
  output:   process.stdout,
  terminal: false
});

rl.on('line', (json) => {
  var data = JSON.parse(json);
  console.log("docGenerator: Running with following input:");
  console.log(data);
  if (data.inputFile != null && data.outputFile != null) {
    var inputFile  = data.inputFile;
    var outputFile = data.outputFile;
    var content    = fs.readFileSync(inputFile, "binary");
    var doc        = new Docxtemplater(content);

    // file params are not intended for template rendering... delete them
    delete data.outputFile;
    delete data.inputFile;
    console.log("docGenerator: Using template - " + inputFile);
    doc.setData(data);
    doc.render();

    var buffer = doc.getZip().generate({type:"nodebuffer"});
    fs.writeFileSync(outputFile, buffer);
    console.log("docGenerator: Resultant document stored in - " + outputFile);
  } else {
    console.error("docGenerator: Error - expecting JSON input with "+
                "required fields {inputFile: '...', outputFile: '...'}");
  }
});
