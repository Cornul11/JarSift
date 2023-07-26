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
  .directive("timer", [
    function () {
      return {
        restrict: "A",
        link: function (scope, element, attrs) {
          function displayTimer() {
            const startTime = parseInt(attrs.timer);
            const time = Math.floor((Date.now() - startTime) / 1000);
            const hours = Math.floor(time / 3600);
            const minutes = Math.floor((time - hours * 3600) / 60);
            const seconds = time - hours * 3600 - minutes * 60;
            element[0].innerHTML = `${hours}h ${minutes}m ${seconds}s`;
          }
          const timeer = setInterval(displayTimer, 1000);
          displayTimer();
          scope.$on("$destroy", (e) => {
            clearInterval(timeer);
          });
        },
      };
    },
  ])
  .controller("mainController", function ($scope, $http, $location) {
    $scope.libraries = [];
    $scope.jarFile = null;
    $scope.isLoading = false;
    $scope.loadingStart = new Date();
    $scope.threshold = 85;

    $scope.clear = function () {
      $scope.libraries = [];
    };
    $scope.$watch("jarFile", function (newValue, oldValue) {
      if (newValue !== oldValue) {
        $scope.analyze();
      }
    });
    $scope.analyze = function () {
      if (!$scope.jarFile) {
        return;
      }
      $scope.loadingStart = new Date();
      $scope.isLoading = true;
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
        })
        .finally(function () {
          $scope.isLoading = false;
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

    $scope.search = function (item) {
      if (!$scope.searchTerm) {
        return true;
      }
      return (
        item.id.toLowerCase().indexOf($scope.searchTerm.toLowerCase()) !== -1
      );
    };

    $scope.hoverLinks = function (links) {
      for (const link of links) {
        document.getElementById(link.target.id).classList.add("hover");
        document.getElementById(link.source.id).classList.add("hover");

        const listElement = document.getElementById("list_" + link.source.id);
        if (listElement) listElement.classList.add("active");
      }
      d3.select("#cluster svg g")
        .selectAll(".hover-link")
        .data(links)
        .join("line")
        .attr("x1", (d) => d.source.x)
        .attr("y1", (d) => d.source.y)
        .attr("x2", (d) => d.target.x)
        .attr("y2", (d) => d.target.y)
        .attr("class", "hover-link")
        .lower();
    };
    $scope.hoverLib = function (lib) {
      if (isNodeClick) {
        return;
      }
      const listElement = document.getElementById("list_" + lib.id);
      listElement.classList.add("active");
      const element = document.getElementById(lib.id);
      element.classList.add("hover");

      const links = simulation
        .force("link")
        .links()
        .filter((d) => d.source.id === lib.id);
      $scope.hoverLinks(links);
    };
    $scope.leaveLib = function () {
      if (isNodeClick) {
        return;
      }
      document
        .querySelectorAll(".node.hover")
        .forEach((e) => e.classList.remove("hover"));
      document
        .querySelectorAll(".active")
        .forEach((e) => e.classList.remove("active"));
      d3.select("#cluster svg g").selectAll(".hover-link").remove();
    };

    let nodes = [];
    let links = [];
    let simulation = null;
    let refresh = null;
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
        // lib
        if (d.lib) {
          $scope.hoverLib(d.lib);
          return;
        }
        // class
        $scope.hoverLinks(links.filter((l) => l.target.id === d.id));
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
            higlight(d);
            isNodeClick = true;
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

      refresh = function () {
        link
          .attr("x1", (d) => d.source.x)
          .attr("y1", (d) => d.source.y)
          .attr("x2", (d) => d.target.x)
          .attr("y2", (d) => d.target.y);
        svg
          .selectAll(".hover-link")
          .attr("x1", (d) => d.source.x)
          .attr("y1", (d) => d.source.y)
          .attr("x2", (d) => d.target.x)
          .attr("y2", (d) => d.target.y);
        node.attr("cx", (d) => d.x).attr("cy", (d) => d.y);
      };
      refresh();

      const refreshInterval = setInterval(refresh, 500);
      let timoutRefresh = null;
      simulation.on("tick", () => {
        if (timoutRefresh) {
          clearTimeout(timoutRefresh);
        }
        timoutRefresh = setTimeout(() => {
          clearInterval(refreshInterval);
        }, 1000);
      });
    };
  });
