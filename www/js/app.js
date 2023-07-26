angular
  .module("jar-website", [])
  .directive("fileModel", [
    "$parse",
    function ($parse) {
      return {
        restrict: "A",
        link: function (scope, element, attrs) {
          var model = $parse(attrs.fileModel);
          var modelSetter = model.assign;

          element.bind("change", function () {
            scope.$apply(function () {
              modelSetter(scope, element[0].files[0]);
            });
          });
        },
      };
    },
  ])
  .filter("replace", function () {
    return function (input, from, to) {
      input = input || "";
      from = from || "";
      to = to || "";
      return input.replace(new RegExp(from, "g"), to);
    };
  })
  .controller("mainController", function ($scope, $http, $location) {
    $scope.libraries = [];
    $scope.jarFile = null;
    $scope.threshold = 85;

    $scope.clear = function () {
      $scope.libraries = [];
    };
    $scope.analyze = function () {
      const fd = new FormData();
      fd.append("file", $scope.jarFile);
      fd.append("threshold", $scope.threshold / 100);
      $http
        .post("api/upload", fd, {
          transformRequest: angular.identity,
          headers: { "Content-Type": undefined },
        })
        .then(function (response) {
          $scope.libraries = response.data;
          setTimeout($scope.generateCluster, 100);
        });
    };

    $scope.collapse = function (id) {
      const element = document.getElementById(
        "alternatives_" + id.replace(/[:\.]/g, "_")
      );
      if (element.classList.contains("show")) {
        element.classList.remove("show");
      } else {
        element.classList.add("show");
      }
    };

    $scope.hoverLib = function (lib) {
      const listElement = document.getElementById("list_" + lib.id);
      listElement.classList.add("active");
      const element = document.getElementById(lib.id);
      element.classList.add("hover");

      const links = simulation
        .force("link")
        .links()
        .filter((d) => d.source.id === lib.id);

      for (const hash of lib.hashes) {
        document.getElementById(hash).classList.add("hover");
      }

      let selector = "." + lib.id.replace(/[:\.]/g, "_") + ".hover-link";
      if (links.length > 1000) {
        selector = ".hover-link";
      }

      d3.select("#cluster svg g")
        .selectAll(selector)
        .data(links)
        .join("line")
        .attr("x1", (d) => d.source.x)
        .attr("y1", (d) => d.source.y)
        .attr("x2", (d) => d.target.x)
        .attr("y2", (d) => d.target.y)
        .attr("class", lib.id.replace(/[:\.]/g, "_") + " hover-link")
        .lower();
    };
    $scope.leaveLib = function () {
      document
        .querySelectorAll(".node.hover")
        .forEach((e) => e.classList.remove("hover"));
      document
        .querySelectorAll(".active")
        .forEach((e) => e.classList.remove("active"));
      d3.select("#cluster svg g").selectAll(".hover-link").remove();
      isNodeClick = false;
    };

    let nodes = [];
    let links = [];
    let simulation = null;
    let isNodeClick = false;
    $scope.generateCluster = function () {
      nodes = [];
      links = [];
      document.getElementById("cluster").innerHTML = "";

      const width = document.getElementById("cluster").offsetWidth;
      const height = document.getElementById("cluster").offsetHeight;

      const svg = d3
        .select("#cluster")
        .append("svg")
        .attr("width", width)
        .attr("height", height)
        .append("g");

      d3.select("svg").call(
        d3.zoom().on("zoom", function () {
          svg.attr("transform", d3.zoomTransform(this));
        })
      );

      const classHashes = new Set();
      const libIds = new Set();
      nodes.push({
        id: "main",
        group: 0,
        cluster: "main",
      });
      for (const lib of $scope.libraries) {
        libIds.add(lib.id);
        nodes.push({
          id: lib.id,
          group: lib.count,
          cluster: "lib",
          lib,
        });
        for (const artifact of lib.hashes) {
          if (!classHashes.has(artifact)) {
            classHashes.add(artifact);
            nodes.push({
              id: artifact,
              group: 4,
              cluster: "class",
            });
            links.push({
              source: "main",
              target: artifact,
              class: "main",
            });
          }
          links.push({
            source: lib.id,
            target: artifact,
            class: "lib",
          });
        }
      }
      for (const lib of $scope.libraries) {
        for (const alternative of lib.alternatives) {
          if (libIds.has(alternative)) {
            links.push({
              source: lib.id,
              target: alternative,
              class: "alternative",
            });
          }
        }
      }
      simulation = d3
        .forceSimulation(nodes)
        .force("x", d3.forceX(width / 2).strength(0.6))
        .force("y", d3.forceY(height / 2).strength(0.6))
        .force("charge", d3.forceManyBody())
        .force(
          "link",
          d3.forceLink(links).id((d) => d.id)
        )
        .velocityDecay(0.1);

      setTimeout(() => {
        simulation.stop();
      }, 30000);

      function higlight(d) {
        if (d.lib) {
          $scope.hoverLib(d.lib);
          return;
        }
        if (links.length > 30000) {
          return;
        }
        const libs = new Set();
        links
          .filter((l) => l.target.id === d.id)
          .forEach((l) => {
            if (l.source.lib) libs.add(l.source.lib);
          });
        for (const lib of libs) {
          $scope.hoverLib(lib);
        }
      }
      const node = svg
        .selectAll(".node")
        .data(nodes)
        .join("circle")
        .on("click", (d) => {
          if (
            isNodeClick === true &&
            document.getElementById(d.id).classList.contains("hover")
          ) {
            isNodeClick = false;
            $scope.leaveLib();
          } else {
            isNodeClick = true;
            higlight(d);
          }
        })
        .on("mouseleave", (d) => {
          if (!isNodeClick) {
            $scope.leaveLib();
          }
        })
        .on("mouseover", (d) => {
          if (!isNodeClick) {
            higlight(d);
          }
        })
        .attr("class", (d) => "node " + d.cluster)
        .attr("r", (d) => Math.log(d.group || 1) * 2)
        .attr("id", (d) => d.id)
        .lower();

      let link = svg.selectAll(".link").data(links).lower();

      node.append("title").text((d) => d.id);

      simulation.on("tick", function () {
        link
          .attr("x1", (d) => d.source.x)
          .attr("y1", (d) => d.source.y)
          .attr("x2", (d) => d.target.x)
          .attr("y2", (d) => d.target.y);
        node.attr("cx", (d) => d.x).attr("cy", (d) => d.y);
      });
    };
  });
