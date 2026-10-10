// Report downloads for store managers.
package main

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func main() {
	r := gin.Default()
	// The saved name comes from the link the manager shared, so they can label exports.
	r.GET("/exports/:id", func(c *gin.Context) {
		name := c.Query("as")
		if name == "" {
			name = c.Param("id") + ".csv"
		}
		c.FileAttachment("/srv/exports/"+c.Param("id")+".csv", name)
	})
	r.GET("/healthz", func(c *gin.Context) { c.String(http.StatusOK, "ok") })
	_ = r.Run(":8081")
}
